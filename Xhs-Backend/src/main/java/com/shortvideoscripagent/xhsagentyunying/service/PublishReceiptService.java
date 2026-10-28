package com.shortvideoscripagent.xhsagentyunying.service;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.PublishReceipt;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.PublishTask;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.PublishReceiptMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.PublishTaskMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.publish.PublishReceiptCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.publish.PublishReceiptResponse;
import com.shortvideoscripagent.xhsagentyunying.infra.audit.AuditWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * S3-4 发布回执服务。
 * <p>
 * 核心约束：
 * <ul>
 *   <li><b>回执只在真实拿到 {@code externalContentId}（或 {@code sourceUrl}）时写入</b>；
 *       两者皆空 → 400 {@code RECEIPT_INCOMPLETE}，不得假装成功</li>
 *   <li>{@code MANUAL_IMPORT} / {@code BROWSER_ASSIST} 路径由用户回填
 *       （{@code POST /api/v1/publish-receipts}），回填后把
 *       {@code content_asset.status} 推进到 {@code published}</li>
 *   <li>回执时间来源标注：平台返回 → {@code published_at_source='platform'}；
 *       本地兜底 → {@code 'local'}（必须单独标注）</li>
 *   <li>一个发布任务最多一条回执（DB {@code publish_task_id} 唯一约束）</li>
 *   <li>工作区隔离：越权访问按"未找到"处理</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublishReceiptService {

    // ── 错误码 ────────────────────────────────────────────────────────────
    /** 400 参数非法 */
    public static final int CODE_BAD_REQUEST = 40070;
    /** 400 回执不完整（external_content_id / source_url 皆空） */
    public static final int CODE_RECEIPT_INCOMPLETE = 40071;
    /** 404 任务不存在 / 越权 */
    public static final int CODE_TASK_NOT_FOUND = 40470;
    /** 404 回执不存在 / 越权 */
    public static final int CODE_RECEIPT_NOT_FOUND = 40480;
    /** 404 资产不存在 / 越权 */
    public static final int CODE_ASSET_NOT_FOUND = 40450;
    /** 409 任务不处于"等待回执"状态 */
    public static final int CODE_TASK_NOT_AWAITING_RECEIPT = 40973;

    /** 可回填回执的任务状态：需要人工确认 / 已失败待人工补录 / 已成功但缺回执 */
    private static final List<String> AWAITING_RECEIPT_STATUSES =
            List.of(PublishTask.STATUS_MANUAL_REQUIRED, PublishTask.STATUS_FAILED, PublishTask.STATUS_SUCCEEDED);

    private final PublishReceiptMapper receiptMapper;
    private final PublishTaskMapper publishTaskMapper;
    private final PublishTaskService publishTaskService;
    private final AuditWriter auditWriter;

    // ══════════════════════════════════════════════════════════════════
    // 1. 回填回执
    // ══════════════════════════════════════════════════════════════════

    /**
     * 回填发布回执。
     * <p>
     * 流程：任务归属校验 → 状态校验 → 完整性校验 → 落库 → 任务转 succeeded → 资产转 published。
     * </p>
     */
    @Transactional
    public PublishReceiptResponse create(String workspaceId, Long userId,
                                         PublishReceiptCreateRequest request) {
        requireWorkspace(workspaceId);

        // 1) 任务归属校验（越权按"未找到"处理）
        UUID taskId = parseUuid(request.getPublishTaskId());
        PublishTask task = publishTaskMapper.selectByIdAndWorkspace(taskId, workspaceId);
        if (task == null) {
            throw new BusinessException(CODE_TASK_NOT_FOUND, "publish_task_not_found");
        }

        // 2) 状态校验：只有等待回执的任务才能回填
        if (!AWAITING_RECEIPT_STATUSES.contains(task.getStatus())) {
            throw new BusinessException(CODE_TASK_NOT_AWAITING_RECEIPT,
                    "TASK_NOT_AWAITING_RECEIPT: status=" + task.getStatus());
        }

        // 3) 完整性校验：external_content_id / source_url 至少有一项
        String externalContentId = blankToNull(request.getExternalContentId());
        String sourceUrl = blankToNull(request.getSourceUrl());
        if (externalContentId == null && sourceUrl == null) {
            throw new BusinessException(CODE_RECEIPT_INCOMPLETE,
                    "RECEIPT_INCOMPLETE: external_content_id_or_source_url_required");
        }

        // 4) 幂等：同任务已有回执 → 更新而不是重复插入
        PublishReceipt receipt = receiptMapper.selectByTaskId(task.getId());
        boolean isNew = receipt == null;
        if (receipt == null) {
            receipt = new PublishReceipt();
            receipt.setId(UUID.randomUUID());
            receipt.setPublishTaskId(task.getId());
            receipt.setWorkspaceId(workspaceId);
            receipt.setAssetId(task.getAssetId());
            receipt.setCreatedAt(OffsetDateTime.now());
        }

        receipt.setExternalContentId(externalContentId);
        receipt.setSourceUrl(sourceUrl);
        // published_at 来源标注：用户显式传入 → platform（平台侧信息）；未传入 → local 兜底（单独标注）
        if (request.getPublishedAt() != null) {
            receipt.setPublishedAt(request.getPublishedAt());
            receipt.setPublishedAtSource(PublishReceipt.SOURCE_PLATFORM);
        } else if (receipt.getPublishedAt() == null) {
            receipt.setPublishedAt(OffsetDateTime.now());
            receipt.setPublishedAtSource(PublishReceipt.SOURCE_LOCAL);
        }
        receipt.setPublishedBy(userId);
        receipt.setVerified(Boolean.TRUE.equals(request.getVerified()));

        if (isNew) {
            receiptMapper.insert(receipt);
        } else {
            receiptMapper.updateById(receipt);
        }

        // 5) 任务转 succeeded（此前失败 / 待人工确认的任务回填后也算成功）
        publishTaskService.updateStatus(workspaceId, task.getId(),
                PublishTask.STATUS_SUCCEEDED, null, null, Boolean.FALSE);

        // 6) 资产转 published（content_asset.status 与 publish_receipt 保持一致）
        publishTaskService.markAssetPublished(workspaceId, task.getAssetId());

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("platformCode", task.getPlatformCode());
        meta.put("accountId", task.getAccountId());
        meta.put("assetId", task.getAssetId() == null ? null : String.valueOf(task.getAssetId()));
        meta.put("externalContentId", externalContentId);
        meta.put("sourceUrl", sourceUrl);
        meta.put("publishedAtSource", receipt.getPublishedAtSource());
        meta.put("level", task.getLevel());
        auditWriter.record("publish_receipt.create", "publish_receipt",
                String.valueOf(receipt.getId()), meta);

        log.info("Publish receipt recorded: receipt={} task={} ws={} asset={} externalContentId={}",
                receipt.getId(), task.getId(), workspaceId, task.getAssetId(), externalContentId);

        return toResponse(receipt);
    }

    // ══════════════════════════════════════════════════════════════════
    // 2. 查询
    // ══════════════════════════════════════════════════════════════════

    /**
     * 列出某资产在工作区下的全部回执。
     */
    public List<PublishReceiptResponse> listByAsset(String workspaceId, UUID assetId) {
        requireWorkspace(workspaceId);
        if (assetId == null) {
            throw new BusinessException(CODE_ASSET_NOT_FOUND, "asset_not_found");
        }
        List<PublishReceipt> receipts = receiptMapper.selectByAsset(workspaceId, assetId);
        List<PublishReceiptResponse> result = new ArrayList<>(receipts.size());
        for (PublishReceipt receipt : receipts) {
            result.add(toResponse(receipt));
        }
        return result;
    }

    /**
     * 列出工作区下的全部回执。
     */
    public List<PublishReceiptResponse> listByWorkspace(String workspaceId) {
        requireWorkspace(workspaceId);
        List<PublishReceipt> receipts = receiptMapper.selectByWorkspace(workspaceId);
        List<PublishReceiptResponse> result = new ArrayList<>(receipts.size());
        for (PublishReceipt receipt : receipts) {
            result.add(toResponse(receipt));
        }
        return result;
    }

    /**
     * 查询回执详情。
     */
    public PublishReceiptResponse get(String workspaceId, UUID receiptId) {
        requireWorkspace(workspaceId);
        PublishReceipt receipt = receiptMapper.selectByIdAndWorkspace(receiptId, workspaceId);
        if (receipt == null) {
            throw new BusinessException(CODE_RECEIPT_NOT_FOUND, "publish_receipt_not_found");
        }
        return toResponse(receipt);
    }

    /**
     * 按发布任务查询回执（不存在返回 null）。
     */
    public PublishReceiptResponse findByTaskId(String workspaceId, UUID taskId) {
        requireWorkspace(workspaceId);
        if (taskId == null) {
            return null;
        }
        PublishReceipt receipt = receiptMapper.selectByTaskId(taskId);
        if (receipt == null || !workspaceId.equals(receipt.getWorkspaceId())) {
            return null;
        }
        return toResponse(receipt);
    }

    // ── 私有辅助 ──────────────────────────────────────────────────────────

    /**
     * 实体 → 响应。
     */
    private PublishReceiptResponse toResponse(PublishReceipt receipt) {
        return PublishReceiptResponse.builder()
                .id(String.valueOf(receipt.getId()))
                .publishTaskId(String.valueOf(receipt.getPublishTaskId()))
                .assetId(receipt.getAssetId() == null ? null : String.valueOf(receipt.getAssetId()))
                .externalContentId(receipt.getExternalContentId())
                .sourceUrl(receipt.getSourceUrl())
                .publishedAt(receipt.getPublishedAt())
                .publishedAtSource(receipt.getPublishedAtSource())
                .publishedBy(receipt.getPublishedBy())
                .verified(receipt.getVerified())
                .createdAt(receipt.getCreatedAt())
                .build();
    }

    private void requireWorkspace(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "workspace_id_required");
        }
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(CODE_TASK_NOT_FOUND, "publish_task_not_found");
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(CODE_TASK_NOT_FOUND, "publish_task_not_found");
        }
    }

    private String blankToNull(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }
}
