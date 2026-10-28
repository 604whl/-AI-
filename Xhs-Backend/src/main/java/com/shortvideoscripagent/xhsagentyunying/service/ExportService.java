package com.shortvideoscripagent.xhsagentyunying.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.common.api.WorkspaceContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.config.AppStorageProperties;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ContentAsset;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ContentAssetMedia;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ExportLog;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ContentAssetMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ContentAssetMediaMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ExportLogMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.export.ExportListItem;
import com.shortvideoscripagent.xhsagentyunying.dto.export.ExportResponse;
import com.shortvideoscripagent.xhsagentyunying.infra.ratelimit.RedisRateLimiter;
import com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.TaskQueueProducer;
import com.shortvideoscripagent.xhsagentyunying.service.export.CsvExporter;
import com.shortvideoscripagent.xhsagentyunying.service.export.DocxGenerator;
import com.shortvideoscripagent.xhsagentyunying.service.export.MarkdownRenderer;
import com.shortvideoscripagent.xhsagentyunying.service.export.ZipPacker;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 导出 Service（P0-5）。
 * <p>
 * 职责：
 * <ul>
 *   <li>限流：每天 50 次/workspace（Redis SETNX + TTL）</li>
 *   <li>投递异步导出任务（Redis Streams）</li>
 *   <li>查询导出状态 / 下载 URL</li>
 *   <li>CSV 同步导出</li>
 * </ul>
 * 实际文件生成由 {@link com.shortvideoscripagent.xhsagentyunying.ai.agent.handler.ExportTaskHandler} 处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private static final int CODE_NOT_FOUND = 40420;
    private static final int CODE_BAD_REQUEST = 40020;
    private static final int CODE_FORBIDDEN = 40310;
    private static final int CODE_RATE_LIMITED = 42901;
    private static final int CODE_STORAGE_ERROR = 50010;

    /** 导出限流：每天 50 次/workspace */
    private static final int EXPORT_DAILY_LIMIT = 50;
    /** 导出文件 bucket */
    private static final String EXPORT_BUCKET = "xhs-exports";

    private final ExportLogMapper exportLogMapper;
    private final ContentAssetMapper contentAssetMapper;
    private final ContentAssetMediaMapper mediaMapper;
    /** TaskQueueProducer 可能不存在（当 app.task-queue.enabled=false 时）。 */
    @Autowired(required = false)
    private TaskQueueProducer taskQueueProducer;
    private final RedisRateLimiter rateLimiter;
    private final StringRedisTemplate redisTemplate;
    private final MarkdownRenderer markdownRenderer;
    private final DocxGenerator docxGenerator;
    private final ZipPacker zipPacker;
    private final CsvExporter csvExporter;
    private final AppStorageProperties storageProperties;
    private final ObjectMapper objectMapper;

    // ════════════════════════════════════════════════════════════════════════
    // 公开 API
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 异步创建导出任务（MD / DOCX / ZIP）。
     *
     * @param workspaceId 工作区 ID
     * @param userId     用户 ID
     * @param assetId    资产 ID
     * @param format     格式：md / docx / zip
     * @return 导出响应（含 exportId）
     */
    @Transactional
    public ExportResponse enqueueExport(String workspaceId, Long userId, UUID assetId, String format) {
        validateFormat(format, false);

        // ── 限流检查（每天 50 次/workspace） ───────────────────────────────────
        String rateLimitKey = buildDailyRateLimitKey(workspaceId);
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(rateLimitKey, "1", Duration.ofDays(1));
        if (Boolean.FALSE.equals(acquired)) {
            Long count = redisTemplate.opsForValue().increment(rateLimitKey);
            if (count != null && count > EXPORT_DAILY_LIMIT) {
                log.warn("Export rate limit exceeded for workspace {}: {}/{}",
                        workspaceId, count, EXPORT_DAILY_LIMIT);
                throw new BusinessException(CODE_RATE_LIMITED,
                        "export_rate_limit_exceeded: max " + EXPORT_DAILY_LIMIT + " per day per workspace");
            }
        } else {
            // 首次写入，手动设置 TTL
            redisTemplate.expire(rateLimitKey, Duration.ofDays(1));
        }

        // ── 创建导出记录 ─────────────────────────────────────────────────────
        UUID exportId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        ExportLog record = new ExportLog();
        record.setId(exportId);
        record.setWorkspaceId(workspaceId);
        record.setUserId(userId);
        record.setAssetId(assetId);
        record.setFormat(format);
        record.setStatus(ExportLog.STATUS_PENDING);
        record.setCreatedAt(now);
        exportLogMapper.insert(record);

        // ── 投递异步任务 ─────────────────────────────────────────────────────
        try {
            if (taskQueueProducer != null) {
                Map<String, Object> payload = Map.of(
                        "exportId", exportId.toString(),
                        "workspaceId", workspaceId.toString(),
                        "userId", userId.toString(),
                        "assetId", assetId != null ? assetId.toString() : "",
                        "format", format
                );
                String payloadJson = objectMapper.writeValueAsString(payload);
                taskQueueProducer.enqueue("EXPORT", exportId.toString(), payloadJson, 1);
            } else {
                // [P1 FIX] task-queue 未启用时，同步降级处理，避免 ExportLog 永远卡在 PENDING。
                // processExport 内部已有幂等保护（status=PENDING 检查 + CAS updateStatusToProcessing），
                // 即使并发查询也不会重复处理。异常由 processExport 内部捕获并标记 FAILED，
                // 不会让请求 500，也不会让记录白等。
                log.info("TaskQueueProducer not available, fallback to sync process for export {}", exportId);
                processExport(exportId);
            }
        } catch (Exception e) {
            log.error("Failed to enqueue export task {}: {}", exportId, e.getMessage());
            // 任务记录已创建，用户可以查询状态
        }

        return toResponse(record, null);
    }

    /**
     * 同步导出 CSV（批量资产列表，适合直接下载）。
     *
     * @param workspaceId 工作区 ID
     * @param userId     用户 ID
     * @param assetIds   要导出的资产 ID 列表（空=导出全部）
     * @return CSV 字节数组
     */
    @Transactional
    public byte[] exportCsv(String workspaceId, Long userId, List<UUID> assetIds) {
        // 限流（同样 50/天/workspace）
        String rateLimitKey = buildDailyRateLimitKey(workspaceId);
        checkDailyRateLimit(workspaceId, rateLimitKey);

        List<ContentAsset> assets;
        if (assetIds == null || assetIds.isEmpty()) {
            assets = contentAssetMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ContentAsset>()
                            .eq("workspace_id", workspaceId)
                            .orderByDesc("updated_at")
            );
        } else {
            assets = contentAssetMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ContentAsset>()
                            .eq("workspace_id", workspaceId)
                            .in("id", assetIds)
                            .orderByDesc("updated_at")
            );
        }

        return csvExporter.export(assets);
    }

    /**
     * 查询导出状态。
     *
     * @param exportId   导出记录 ID
     * @param userId     用户 ID（用于权限校验）
     * @param workspaceId 工作区 ID
     * @return 导出响应
     */
    public ExportResponse getExportStatus(UUID exportId, Long userId, String workspaceId) {
        ExportLog record = exportLogMapper.selectById(exportId);
        if (record == null) {
            throw new BusinessException(CODE_NOT_FOUND, "export_not_found");
        }
        if (!record.getWorkspaceId().equals(workspaceId)) {
            throw new BusinessException(CODE_NOT_FOUND, "export_not_found");
        }

        String downloadUrl = null;
        if (ExportLog.STATUS_READY.equals(record.getStatus())) {
            try {
                downloadUrl = buildSignedUrl(record.getObjectKey());
            } catch (Exception e) {
                log.warn("Failed to build signed URL for export {}: {}", exportId, e.getMessage());
            }
        }

        return toResponse(record, downloadUrl);
    }

    /**
     * 获取下载 URL（校验权限 + 过期检查）。
     *
     * @param exportId   导出记录 ID
     * @param userId     用户 ID
     * @param workspaceId 工作区 ID
     * @return 签名 URL
     */
    public String getDownloadUrl(UUID exportId, Long userId, String workspaceId) {
        ExportLog record = exportLogMapper.selectById(exportId);
        if (record == null) {
            throw new BusinessException(CODE_NOT_FOUND, "export_not_found");
        }
        if (!record.getWorkspaceId().equals(workspaceId)) {
            throw new BusinessException(CODE_NOT_FOUND, "export_not_found");
        }

        if (!ExportLog.STATUS_READY.equals(record.getStatus())) {
            throw new BusinessException(CODE_BAD_REQUEST,
                    "export_not_ready: status=" + record.getStatus());
        }

        if (record.getExpiresAt() != null && record.getExpiresAt().isBefore(OffsetDateTime.now())) {
            // 标记过期
            record.setStatus(ExportLog.STATUS_EXPIRED);
            exportLogMapper.updateById(record);
            throw new BusinessException(CODE_NOT_FOUND, "export_url_expired");
        }

        return buildSignedUrl(record.getObjectKey());
    }

    /**
     * 分页查询导出记录列表。
     */
    public List<ExportListItem> listExports(String workspaceId, Pageable pageable) {
        long offset = pageable.getOffset();
        int pageNo = (int) (offset / pageable.getPageSize()) + 1;
        int pageSize = (int) pageable.getPageSize();

        Page<ExportLog> page = new Page<>(pageNo, pageSize);
        IPage<ExportLog> result = exportLogMapper.selectPageByWorkspace(page, workspaceId);

        return result.getRecords().stream()
                .map(this::toListItem)
                .toList();
    }

    /**
     * 统计导出记录总数。
     */
    public long countExports(String workspaceId) {
        return exportLogMapper.countByWorkspace(workspaceId);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Worker 调用：处理导出（由 ExportTaskHandler 触发）
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 处理导出任务（Worker 线程调用）。
     * 生成文件并上传 MinIO。
     */
    public void processExport(UUID exportId) {
        ExportLog record = exportLogMapper.selectById(exportId);
        if (record == null) {
            log.error("Export record not found: {}", exportId);
            return;
        }

        if (!ExportLog.STATUS_PENDING.equals(record.getStatus())) {
            log.warn("Export {} not in pending state, skipping: {}", exportId, record.getStatus());
            return;
        }

        // CAS 更新状态为 processing（防止重复处理）
        int updated = exportLogMapper.updateStatusToProcessing(exportId);
        if (updated == 0) {
            log.warn("Export {} already processing, skipping", exportId);
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();

        try {
            UUID assetId = record.getAssetId();
            if (assetId == null) {
                throw new BusinessException(CODE_BAD_REQUEST, "asset_id_required");
            }

            ContentAsset asset = contentAssetMapper.selectById(assetId);
            if (asset == null) {
                throw new BusinessException(CODE_NOT_FOUND, "asset_not_found");
            }

            // 加载封面图
            ContentAssetMedia coverMedia = mediaMapper.selectCoverByAssetId(assetId);
            byte[] coverBytes = null;
            String coverMimeType = "image/jpeg";

            if (coverMedia != null && coverBytes == null) {
                try {
                    MinioClient client = buildMinioClient();
                    String bucket = coverMedia.getObjectKey().contains("/")
                            ? coverMedia.getObjectKey().split("/")[0]
                            : storageProperties.getMinio().getBucket();
                    String objectKey = coverMedia.getObjectKey();
                    var resp = client.getObject(
                            GetObjectArgs.builder()
                                    .bucket(bucket)
                                    .object(objectKey)
                                    .build()
                    );
                    coverBytes = resp.readAllBytes();
                } catch (Exception e) {
                    log.warn("Failed to load cover image for asset {}: {}", assetId, e.getMessage());
                }
            }

            // 生成文件
            byte[] fileBytes;
            String extension;
            String mimeType;

            switch (record.getFormat()) {
                case ExportLog.FORMAT_MD -> {
                    fileBytes = markdownRenderer.render(asset,
                            coverMedia != null ? coverMedia.getObjectKey() : null, false);
                    extension = ".md";
                    mimeType = "text/markdown; charset=utf-8";
                }
                case ExportLog.FORMAT_DOCX -> {
                    fileBytes = docxGenerator.generate(asset, coverBytes, coverMimeType);
                    extension = ".docx";
                    mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                }
                case ExportLog.FORMAT_ZIP -> {
                    fileBytes = zipPacker.pack(asset, coverBytes, "cover.jpg", coverMimeType);
                    extension = ".zip";
                    mimeType = "application/zip";
                }
                default -> throw new BusinessException(CODE_BAD_REQUEST,
                        "unsupported_format: " + record.getFormat());
            }

            // 上传 MinIO
            String objectKey = uploadToMinio(record, fileBytes, mimeType);

            // 更新状态为 ready
            OffsetDateTime expiresAt = now.plusSeconds(ExportLog.SIGNED_URL_EXPIRY_SECONDS);
            exportLogMapper.updateStatusToReady(exportId, objectKey,
                    (long) fileBytes.length, expiresAt, now);

            log.info("Export {} completed: {} bytes -> {}", exportId, fileBytes.length, objectKey);

        } catch (Exception e) {
            log.error("Export {} failed: {}", exportId, e.getMessage(), e);
            String msg = e instanceof BusinessException be
                    ? be.getMessage() : "internal_error: " + e.getMessage();
            exportLogMapper.updateStatusToFailed(exportId, msg, now);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 私有方法
    // ════════════════════════════════════════════════════════════════════════

    private void validateFormat(String format, boolean allowCsv) {
        List<String> valid = allowCsv
                ? List.of(ExportLog.FORMAT_MD, ExportLog.FORMAT_DOCX,
                        ExportLog.FORMAT_ZIP, ExportLog.FORMAT_CSV)
                : List.of(ExportLog.FORMAT_MD, ExportLog.FORMAT_DOCX, ExportLog.FORMAT_ZIP);

        if (!valid.contains(format)) {
            throw new BusinessException(CODE_BAD_REQUEST,
                    "invalid_format: must be one of " + valid);
        }
    }

    private void checkDailyRateLimit(String workspaceId, String rateLimitKey) {
        Long count = redisTemplate.opsForValue().increment(rateLimitKey);
        if (count != null && count == 1) {
            redisTemplate.expire(rateLimitKey, Duration.ofDays(1));
        }
        if (count != null && count > EXPORT_DAILY_LIMIT) {
            throw new BusinessException(CODE_RATE_LIMITED,
                    "export_rate_limit_exceeded: max " + EXPORT_DAILY_LIMIT + " per day per workspace");
        }
    }

    private String buildDailyRateLimitKey(String workspaceId) {
        String dateStr = java.time.LocalDate.now().toString(); // yyyy-MM-dd
        return "export:daily:" + workspaceId + ":" + dateStr;
    }

    private String buildSignedUrl(String objectKey) {
        try {
            AppStorageProperties.Minio minio = storageProperties.getMinio();
            MinioClient client = buildMinioClient();

            return client.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(minio.getBucket())
                            .object(objectKey)
                            .expiry(ExportLog.SIGNED_URL_EXPIRY_SECONDS)
                            .method(Method.GET)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to build signed URL for {}: {}", objectKey, e.getMessage());
            throw new BusinessException(CODE_STORAGE_ERROR, "failed_to_generate_download_url");
        }
    }

    private MinioClient buildMinioClient() {
        AppStorageProperties.Minio minio = storageProperties.getMinio();
        return MinioClient.builder()
                .endpoint(minio.getEndpoint())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();
    }

    private String uploadToMinio(ExportLog record, byte[] data, String contentType) throws Exception {
        AppStorageProperties.Minio minio = storageProperties.getMinio();
        MinioClient client = buildMinioClient();

        // 确保 bucket 存在
        try {
            client.bucketExists(
                    io.minio.BucketExistsArgs.builder().bucket(minio.getBucket()).build()
            );
        } catch (Exception e) {
            client.makeBucket(
                    io.minio.MakeBucketArgs.builder().bucket(minio.getBucket()).build()
            );
        }

        String objectKey = String.format("exports/%s/%s/%s%s",
                record.getWorkspaceId(),
                record.getFormat(),
                record.getId(),
                ".md".equals(record.getFormat()) ? ".md" :
                        ".docx".equals(record.getFormat()) ? ".docx" :
                                ".zip".equals(record.getFormat()) ? ".zip" : ".bin"
        );

        try (ByteArrayInputStream bais = new ByteArrayInputStream(data)) {
            client.putObject(
                    PutObjectArgs.builder()
                            .bucket(minio.getBucket())
                            .object(objectKey)
                            .stream(bais, data.length, -1)
                            .contentType(contentType)
                            .build()
            );
        }

        return objectKey;
    }

    private ExportResponse toResponse(ExportLog record, String downloadUrl) {
        return ExportResponse.builder()
                .id(record.getId())
                .format(record.getFormat())
                .status(record.getStatus())
                .errorMessage(record.getErrorMessage())
                .downloadUrl(downloadUrl)
                .fileSize(record.getFileSize())
                .expiresAt(record.getExpiresAt())
                .createdAt(record.getCreatedAt())
                .finishedAt(record.getFinishedAt())
                .build();
    }

    private ExportListItem toListItem(ExportLog record) {
        return ExportListItem.builder()
                .id(record.getId())
                .format(record.getFormat())
                .status(record.getStatus())
                .errorMessage(record.getErrorMessage())
                .fileSize(record.getFileSize())
                .expiresAt(record.getExpiresAt())
                .createdAt(record.getCreatedAt())
                .finishedAt(record.getFinishedAt())
                .build();
    }
}
