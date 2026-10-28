package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shortvideoscripagent.xhsagentyunying.config.mybatis.JsonbStringTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * S3-4 发布回执实体（对应 {@code app.publish_receipt}，V56）。
 * <p>
 * <b>硬性约束</b>：只在真实拿到 {@code externalContentId} 或 {@code sourceUrl} 时才写入；
 * 两者皆空视为回执不完整，任务必须标 {@code failed} 且 {@code errorCode=RECEIPT_INCOMPLETE}，
 * 不得假装成功。
 * <p>
 * {@code publishedAtSource} 区分发布时间来源：{@code platform}（平台返回）/ {@code local}（本地兜底）。
 */
@Data
@TableName(value = "publish_receipt", autoResultMap = true)
public class PublishReceipt {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** 关联发布任务（唯一：一个任务最多一条回执） */
    private UUID publishTaskId;

    /** 所属工作区（VARCHAR(32)，与 app.workspace.id 一致） */
    private String workspaceId;

    /** 关联内容资产 */
    private UUID assetId;

    /** 平台侧内容 ID（人工路径由用户回填，禁止伪造） */
    private String externalContentId;

    /** 平台侧内容链接 */
    private String sourceUrl;

    /** 实际发布时间 */
    private OffsetDateTime publishedAt;

    /** 发布时间来源：platform / local */
    private String publishedAtSource;

    /** 回填人 */
    private Long publishedBy;

    /** 平台原始回包 JSONB（不得写入凭据明文） */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String rawResponse;

    /** 是否已核验 */
    private Boolean verified;

    private OffsetDateTime createdAt;

    // ── 常量 ────────────────────────────────────────────────────────────

    public static final String SOURCE_PLATFORM = "platform";
    public static final String SOURCE_LOCAL = "local";
}
