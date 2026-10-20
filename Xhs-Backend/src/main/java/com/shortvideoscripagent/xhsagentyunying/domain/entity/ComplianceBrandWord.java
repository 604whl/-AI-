package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 品牌级禁用词实体（S2-3: 每个 workspace 独立配置）。
 * 对应表 app.compliance_brand_word。
 */
@Data
@TableName("compliance_brand_word")
public class ComplianceBrandWord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private UUID workspaceId;

    private String ruleCode;

    private String keyword;

    private String riskLevel; // LOW / MEDIUM / HIGH / BLOCKED

    private Long createdBy;

    private OffsetDateTime createdAt;
}