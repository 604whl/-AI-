package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.compliance.ComplianceChecker;
import com.shortvideoscripagent.xhsagentyunying.dto.compliance.ComplianceScanRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.compliance.ComplianceScanResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/compliance")
@RequiredArgsConstructor
public class ComplianceController {

    private static final int CODE_UNAUTHORIZED = 40101;

    private final ComplianceChecker complianceChecker;

    @PostMapping("/scan")
    public ApiResponse<ComplianceScanResponse> scan(@RequestBody(required = false) ComplianceScanRequest request) {
        requireUserId();
        ComplianceScanRequest body = request == null ? new ComplianceScanRequest() : request;
        return ApiResponse.ok(ComplianceScanResponse.builder()
                .warnings(complianceChecker.scan(body.getTitle(), body.getBody()))
                .build());
    }

    private Long requireUserId() {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        }
        return userId;
    }
}
