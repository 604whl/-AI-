package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.import_.CsvImportRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.import_.CsvImportResponse;
import com.shortvideoscripagent.xhsagentyunying.service.CsvImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/import")
@RequiredArgsConstructor
public class CsvImportController {

    private static final int CODE_UNAUTHORIZED = 40101;

    private final CsvImportService csvImportService;

    @PostMapping("/csv")
    public ApiResponse<CsvImportResponse> importCsv(@Valid @RequestBody CsvImportRequest request) {
        Long userId = requireUserId();
        return ApiResponse.ok(csvImportService.importCsv(userId, request));
    }

    private Long requireUserId() {
        Long uid = RequestContext.getUserId();
        if (uid == null) {
            throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        }
        return uid;
    }
}