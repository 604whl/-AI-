package com.shortvideoscripagent.xhsagentyunying.dto.compliance;

import lombok.Data;

import java.util.List;

/**
 * 合规规则 DTO（S2-3: 管理接口返回结构）。
 */
@Data
public class ComplianceRuleDTO {

    private Long id;
    private String code;
    private String displayName;
    private String categoryCode;
    private String riskLevel;
    private String description;
    private String detectorType;
    private List<String> keywords;
    private List<String> patterns;
    private Boolean enabled;
}