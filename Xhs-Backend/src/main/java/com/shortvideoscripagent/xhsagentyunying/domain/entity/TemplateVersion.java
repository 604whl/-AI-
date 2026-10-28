package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shortvideoscripagent.xhsagentyunying.config.mybatis.JsonbStringTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 模板版本历史快照（P0-4）。
 * <p>
 * 每次发布新版本时写入一条不可变记录。
 */
@Data
@TableName(value = "template_version", autoResultMap = true)
public class TemplateVersion {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /** 所属模板 ID */
    private UUID templateId;

    /** 版本号，从 1 开始递增 */
    private Integer versionNo;

    /**
     * 完整快照（JSONB）。
     * 包含：title, body_template, variables, default_goal, default_tone,
     *       category, scenario, persona
     */
        @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String snapshot;  // JSONB → MyBatis-Plus 存 String

    /** 发布时间 */
    private OffsetDateTime publishedAt;

    /** 发布人 userId */
    private Long publishedBy;
}
