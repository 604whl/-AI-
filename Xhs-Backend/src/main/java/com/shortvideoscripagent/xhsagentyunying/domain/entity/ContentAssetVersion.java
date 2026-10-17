package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 内容资产版本历史（P0-3）。
 * <p>
 * 每次修改时插入一条新记录（不可变），rollback 时写入新版本号。
 */
@Data
@TableName("content_asset_version")
public class ContentAssetVersion {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** 所属资产 ID */
    private UUID assetId;

    /** 版本号，从 1 开始递增 */
    private Integer versionNo;

    /** 该版本的快照标题 */
    private String title;

    /** 该版本的快照正文 */
    private String body;

    /** 编辑人 userId */
    private Long editedBy;

    /** 编辑原因（可选） */
    private String editReason;

    /** 创建时间 */
    private OffsetDateTime createdAt;
}
