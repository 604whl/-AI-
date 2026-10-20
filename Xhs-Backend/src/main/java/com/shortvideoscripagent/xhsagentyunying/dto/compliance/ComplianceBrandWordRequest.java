package com.shortvideoscripagent.xhsagentyunying.dto.compliance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 品牌禁用词请求 DTO（S2-3: POST /v1/compliance/brand-words）。
 */
@Data
public class ComplianceBrandWordRequest {

    @NotBlank
    private String ruleCode;

    @NotBlank
    @Size(max = 256)
    private String keyword;

    @Pattern(regexp = "LOW|MEDIUM|HIGH|BLOCKED")
    private String riskLevel = "HIGH";
}