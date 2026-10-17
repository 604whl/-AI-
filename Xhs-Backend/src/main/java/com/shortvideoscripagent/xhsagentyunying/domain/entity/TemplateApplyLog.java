package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 模板应用记录（P0-4）。
 * <p>
 * 记录每次将模板渲染后应用到内容资产的操作，用于统计和追溯。
 */
@Data
@TableName("template_apply_log")
public class TemplateApplyLog {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** 关联的模板 ID */
    private UUID templateId;

    /** 关联的内容资产 ID（可能为 null，如果资产被删除） */
    private UUID assetId;

    /** 工作区 ID */
    private UUID workspaceId;

    /** 执行操作的用户 ID */
    private Long userId;

    /** 创建时间 */
    private OffsetDateTime createdAt;
}
