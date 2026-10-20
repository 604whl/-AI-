package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 合规规则实体（S2-3: DB-backed 规则，支持热更新）。
 * 对应表 app.compliance_rule。
 */
@Data
@TableName("compliance_rule")
public class ComplianceRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String displayName;

    private String categoryCode;

    private String riskLevel;

    private String description;

    private String detectorType;

    private com.fasterxml.jackson.databind.JsonNode detectorConfig;

    private com.fasterxml.jackson.databind.JsonNode sampleInputs;

    private Boolean enabled;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}