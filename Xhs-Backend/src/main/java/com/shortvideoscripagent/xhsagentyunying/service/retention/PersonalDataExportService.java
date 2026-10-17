package com.shortvideoscripagent.xhsagentyunying.service.retention;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.config.AppStorageProperties;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.*;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.*;
import com.shortvideoscripagent.xhsagentyunying.dto.retention.ExportRequestResponse;
import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 个人数据导出服务（P0-9）。
 * <p>
 * 支持个人数据导出和工作区全量导出，打包为 ZIP 存储在 MinIO，有效期 24 小时。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalDataExportService {

    private static final int CODE_NOT_FOUND = 40401;
    private static final int CODE_BAD_REQUEST = 40001;

    /** 导出文件过期时间（小时） */
    private static final long EXPORT_EXPIRY_HOURS = 24L;

    private final ExportRequestMapper exportRequestMapper;
    private final UserMapper userMapper;
    private final AnalysisTaskMapper analysisTaskMapper;
    private final ContentAssetMapper contentAssetMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ApiKeyMapper apiKeyMapper;
    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceMemberMapper workspaceMemberMapper;
    private final TemplateMapper templateMapper;
    private final ExportLogMapper exportLogMapper;
    private final AppStorageProperties storageProperties;
    private final ObjectMapper objectMapper;

    // ═══════════════════════════════════════════════════════════════════════════
    // 公开 API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 请求个人数据导出。
     *
     * @param userId 用户 ID
     * @return 导出任务信息
     */
    @Transactional
    public ExportRequestResponse requestPersonalExport(Long userId) {
        User user = requireUser(userId);

        // 获取用户所属的工作区
        List<WorkspaceMember> memberships = workspaceMemberMapper.selectList(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getUserId, userId)
                        .eq(WorkspaceMember::getStatus, "active")
        );

        if (memberships.isEmpty()) {
            throw new BusinessException(CODE_BAD_REQUEST, "user_has_no_workspace");
        }

        // 使用第一个 workspace 存储导出文件
        String workspaceId = memberships.get(0).getWorkspaceId();

        // 创建导出请求记录
        ExportRequest request = new ExportRequest();
        request.setId(UUID.randomUUID());
        request.setWorkspaceId(workspaceId);
        request.setUserId(userId);
        request.setExportType(ExportRequest.TYPE_PERSONAL_DATA);
        request.setStatus(ExportRequest.STATUS_PENDING);
        request.setCreatedAt(OffsetDateTime.now());
        exportRequestMapper.insert(request);

        // 异步执行导出
        exportPersonalDataAsync(request);

        return buildResponse(request, null);
    }

    /**
     * 请求工作区全量导出。
     *
     * @param workspaceId 工作区 ID
     * @param userId     操作用户 ID
     * @return 导出任务信息
     */
    @Transactional
    public ExportRequestResponse requestWorkspaceExport(String workspaceId, Long userId) {
        // 校验 workspace 存在
        Workspace workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null) {
            throw new BusinessException(CODE_NOT_FOUND, "workspace_not_found");
        }

        // 创建导出请求记录
        ExportRequest request = new ExportRequest();
        request.setId(UUID.randomUUID());
        request.setWorkspaceId(workspaceId);
        request.setUserId(userId);
        request.setExportType(ExportRequest.TYPE_WORKSPACE_DATA);
        request.setStatus(ExportRequest.STATUS_PENDING);
        request.setCreatedAt(OffsetDateTime.now());
        exportRequestMapper.insert(request);

        // 异步执行导出
        exportWorkspaceDataAsync(request);

        return buildResponse(request, null);
    }

    /**
     * 查询导出任务状态。
     *
     * @param exportId 导出请求 ID
     * @param userId   操作用户 ID
     * @return 导出任务信息
     */
    public ExportRequestResponse getExportStatus(UUID exportId, Long userId) {
        ExportRequest request = exportRequestMapper.selectById(exportId);
        if (request == null) {
            throw new BusinessException(CODE_NOT_FOUND, "export_request_not_found");
        }

        // 校验权限
        if (!request.getUserId().equals(userId)) {
            throw new BusinessException(CODE_NOT_FOUND, "export_request_not_found");
        }

        // 如果 ready，生成下载链接
        String downloadUrl = null;
        if (request.isReady()) {
            downloadUrl = generatePresignedUrl(request);
        }

        return buildResponse(request, downloadUrl);
    }

    /**
     * 查询用户的导出历史。
     *
     * @param userId 用户 ID
     * @return 导出请求列表
     */
    public List<ExportRequestResponse> listMyExports(Long userId) {
        return exportRequestMapper.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(req -> {
                    String downloadUrl = null;
                    if (req.isReady()) {
                        downloadUrl = generatePresignedUrl(req);
                    }
                    return buildResponse(req, downloadUrl);
                })
                .toList();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 异步导出实现
    // ═══════════════════════════════════════════════════════════════════════════

    @Async
    public void exportPersonalDataAsync(ExportRequest request) {
        try {
            updateStatus(request.getId(), ExportRequest.STATUS_PROCESSING);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                // 1. 用户信息
                addJsonEntry(zos, "user_profile.json", buildUserProfile(request.getUserId()));

                // 2. 分析任务
                addJsonEntry(zos, "analysis_tasks.json",
                        analysisTaskMapper.selectList(
                                new LambdaQueryWrapper<AnalysisTask>()
                                        .eq(AnalysisTask::getUserId, request.getUserId())
                                        .orderByDesc(AnalysisTask::getCreatedAt)
                                        .last("LIMIT 1000")
                        ));

                // 3. 内容资产（该用户拥有的）
                addJsonEntry(zos, "content_assets.json",
                        contentAssetMapper.selectList(
                                new LambdaQueryWrapper<ContentAsset>()
                                        .eq(ContentAsset::getOwnerUserId, request.getUserId())
                                        .orderByDesc(ContentAsset::getCreatedAt)
                                        .last("LIMIT 1000")
                        ));

                // 4. 聊天会话
                List<ChatSession> sessions = chatSessionMapper.selectList(
                        new LambdaQueryWrapper<ChatSession>()
                                .eq(ChatSession::getUserId, request.getUserId())
                                .orderByDesc(ChatSession::getCreatedAt)
                                .last("LIMIT 100")
                );
                addJsonEntry(zos, "chat_sessions.json", sessions);

                // 5. API Key 列表
                addJsonEntry(zos, "api_keys.json",
                        apiKeyMapper.selectList(
                                new LambdaQueryWrapper<ApiKey>()
                                        .eq(ApiKey::getCreatedBy, request.getUserId())
                                        .orderByDesc(ApiKey::getCreatedAt)
                        ));

                // 6. 工作区信息
                List<WorkspaceMember> memberships = workspaceMemberMapper.selectList(
                        new LambdaQueryWrapper<WorkspaceMember>()
                                .eq(WorkspaceMember::getUserId, request.getUserId())
                );
                List<Map<String, Object>> wsInfo = memberships.stream()
                        .map(m -> {
                            Map<String, Object> info = new HashMap<>();
                            info.put("workspaceId", m.getWorkspaceId());
                            info.put("role", m.getRole());
                            info.put("joinedAt", m.getJoinedAt());
                            info.put("status", m.getStatus());
                            Workspace ws = workspaceMapper.selectById(m.getWorkspaceId());
                            if (ws != null) {
                                info.put("workspaceName", ws.getName());
                                info.put("workspaceSlug", ws.getSlug());
                            }
                            return info;
                        })
                        .toList();
                addJsonEntry(zos, "workspaces.json", wsInfo);

                // 7. 导出报告
                Map<String, Object> report = new HashMap<>();
                report.put("exportType", "personal_data");
                report.put("exportedAt", OffsetDateTime.now().toString());
                report.put("userId", request.getUserId());
                addJsonEntry(zos, "export_report.json", report);
            }

            // 上传到 MinIO
            String objectKey = uploadToMinio(request.getWorkspaceId(),
                    request.getId().toString(), baos.toByteArray());

            // 更新状态
            OffsetDateTime now = OffsetDateTime.now();
            request.setStatus(ExportRequest.STATUS_READY);
            request.setObjectKey(objectKey);
            request.setFileSize((long) baos.size());
            request.setExpiresAt(now.plusHours(EXPORT_EXPIRY_HOURS));
            request.setFinishedAt(now);
            exportRequestMapper.updateById(request);

            log.info("Personal data export {} completed, objectKey={}, size={}",
                    request.getId(), objectKey, baos.size());

        } catch (Exception e) {
            log.error("Personal data export {} failed: {}", request.getId(), e.getMessage(), e);
            updateStatus(request.getId(), ExportRequest.STATUS_FAILED);
            updateErrorMessage(request.getId(), e.getMessage());
        }
    }

    @Async
    public void exportWorkspaceDataAsync(ExportRequest request) {
        try {
            updateStatus(request.getId(), ExportRequest.STATUS_PROCESSING);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                String workspaceId = request.getWorkspaceId();

                // 1. 工作区信息
                Workspace workspace = workspaceMapper.selectById(workspaceId);
                if (workspace != null) {
                    Map<String, Object> wsInfo = new HashMap<>();
                    wsInfo.put("id", workspace.getId());
                    wsInfo.put("name", workspace.getName());
                    wsInfo.put("slug", workspace.getSlug());
                    wsInfo.put("industry", workspace.getIndustry());
                    wsInfo.put("region", workspace.getRegion());
                    wsInfo.put("defaultLocale", workspace.getDefaultLocale());
                    wsInfo.put("createdAt", workspace.getCreatedAt());
                    addJsonEntry(zos, "workspace.json", wsInfo);
                }

                // 2. 成员列表
                addJsonEntry(zos, "members.json",
                        workspaceMemberMapper.selectList(
                                new LambdaQueryWrapper<WorkspaceMember>()
                                        .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                        ));

                // 3. 内容资产
                addJsonEntry(zos, "content_assets.json",
                        contentAssetMapper.selectList(
                                new LambdaQueryWrapper<ContentAsset>()
                                        .eq(ContentAsset::getWorkspaceId, workspaceId)
                        ));

                // 4. 模板
                addJsonEntry(zos, "templates.json",
                        templateMapper.selectList(
                                new LambdaQueryWrapper<Template>()
                                        .eq(Template::getWorkspaceId, workspaceId)
                        ));

                // 5. API Keys
                addJsonEntry(zos, "api_keys.json",
                        apiKeyMapper.selectList(
                                new LambdaQueryWrapper<ApiKey>()
                                        .eq(ApiKey::getWorkspaceId, workspaceId)
                        ));

                // 6. 导出记录
                addJsonEntry(zos, "exports.json",
                        exportLogMapper.selectList(
                                new LambdaQueryWrapper<ExportLog>()
                                        .eq(ExportLog::getWorkspaceId, workspaceId)
                        ));

                // 7. 导出报告
                Map<String, Object> report = new HashMap<>();
                report.put("exportType", "workspace_data");
                report.put("exportedAt", OffsetDateTime.now().toString());
                report.put("workspaceId", workspaceId);
                report.put("exportedBy", request.getUserId());
                addJsonEntry(zos, "export_report.json", report);
            }

            // 上传到 MinIO
            String objectKey = uploadToMinio(request.getWorkspaceId(),
                    request.getId().toString(), baos.toByteArray());

            // 更新状态
            OffsetDateTime now = OffsetDateTime.now();
            request.setStatus(ExportRequest.STATUS_READY);
            request.setObjectKey(objectKey);
            request.setFileSize((long) baos.size());
            request.setExpiresAt(now.plusHours(EXPORT_EXPIRY_HOURS));
            request.setFinishedAt(now);
            exportRequestMapper.updateById(request);

            log.info("Workspace data export {} completed, objectKey={}, size={}",
                    request.getId(), objectKey, baos.size());

        } catch (Exception e) {
            log.error("Workspace data export {} failed: {}", request.getId(), e.getMessage(), e);
            updateStatus(request.getId(), ExportRequest.STATUS_FAILED);
            updateErrorMessage(request.getId(), e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 私有辅助方法
    // ═══════════════════════════════════════════════════════════════════════════

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(CODE_NOT_FOUND, "user_not_found");
        }
        return user;
    }

    private void addJsonEntry(ZipOutputStream zos, String entryName, Object data) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        zos.write(json.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private Map<String, Object> buildUserProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Map.of("id", userId, "error", "user not found");
        }
        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getId());
        profile.put("email", user.getEmail());
        profile.put("displayName", user.getDisplayName());
        profile.put("defaultPersona", user.getDefaultPersona());
        profile.put("dailyQuota", user.getDailyQuota());
        profile.put("createdAt", user.getCreatedAt());
        profile.put("updatedAt", user.getUpdatedAt());
        return profile;
    }

    private String uploadToMinio(String workspaceId, String exportId, byte[] data) throws Exception {
        AppStorageProperties.Minio minio = storageProperties.getMinio();
        MinioClient client = MinioClient.builder()
                .endpoint(minio.getEndpoint())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();

        String bucket = minio.getBucket();
        String objectKey = "exports/" + workspaceId + "/" + exportId + ".zip";

        // 确保 bucket 存在
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(data)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(bais, data.length, -1)
                    .contentType("application/zip")
                    .build());
        }

        return objectKey;
    }

    private String generatePresignedUrl(ExportRequest request) {
        if (request.getObjectKey() == null) {
            return null;
        }
        try {
            AppStorageProperties.Minio minio = storageProperties.getMinio();
            MinioClient client = MinioClient.builder()
                    .endpoint(minio.getEndpoint())
                    .credentials(minio.getAccessKey(), minio.getSecretKey())
                    .build();

            return client.getPresignedObjectUrl(
                    io.minio.GetPresignedObjectUrlArgs.builder()
                            .bucket(minio.getBucket())
                            .object(request.getObjectKey())
                            .expiry((int) Duration.ofHours(EXPORT_EXPIRY_HOURS).toSeconds())
                            .build());
        } catch (Exception e) {
            log.warn("Failed to generate presigned URL for {}: {}", request.getId(), e.getMessage());
            return null;
        }
    }

    @Transactional
    public void updateStatus(UUID requestId, String status) {
        ExportRequest request = exportRequestMapper.selectById(requestId);
        if (request != null) {
            request.setStatus(status);
            exportRequestMapper.updateById(request);
        }
    }

    @Transactional
    public void updateErrorMessage(UUID requestId, String errorMessage) {
        ExportRequest request = exportRequestMapper.selectById(requestId);
        if (request != null) {
            request.setErrorMessage(errorMessage);
            request.setFinishedAt(OffsetDateTime.now());
            exportRequestMapper.updateById(request);
        }
    }

    private ExportRequestResponse buildResponse(ExportRequest request, String downloadUrl) {
        return ExportRequestResponse.builder()
                .id(request.getId().toString())
                .exportType(request.getExportType())
                .status(request.getStatus())
                .downloadUrl(downloadUrl)
                .fileSize(request.getFileSize())
                .expiresAt(request.getExpiresAt())
                .errorMessage(request.getErrorMessage())
                .createdAt(request.getCreatedAt())
                .finishedAt(request.getFinishedAt())
                .build();
    }
}
