package com.shortvideoscripagent.xhsagentyunying.dto.asset;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 内容资产响应（P0-3）。
 */
@Data
@Builder
public class AssetResponse {

    private UUID id;

    /** Workspace ID（VARCHAR(32)，与 app.workspace.id 一致） */
    private String workspaceId;

    private Long ownerUserId;
    private String title;
    private String body;
    private String persona;
    private String scenario;
    private String industry;
    private String status;
    private UUID sourceTaskId;

    /** P0-4: 关联的模板 ID */
    private UUID templateId;

    private String platform;
    private String accountId;
    private Integer currentVersion;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /** 关联的多媒体列表 */
    private List<MediaItem> media;

    /** 多标签列表 */
    private List<String> tags;

    /** 关联的分析报告摘要（可选） */
    private Map<String, Object> analysisReport;
}
