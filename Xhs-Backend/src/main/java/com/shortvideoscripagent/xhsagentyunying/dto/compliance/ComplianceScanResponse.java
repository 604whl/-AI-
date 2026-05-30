package com.shortvideoscripagent.xhsagentyunying.dto.compliance;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class ComplianceScanResponse {

    private List<Map<String, Object>> warnings;
}
