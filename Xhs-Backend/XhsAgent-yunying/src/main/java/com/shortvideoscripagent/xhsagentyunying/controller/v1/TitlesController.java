package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.title.TitleGenerateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.title.TitleGenerateResponse;
import com.shortvideoscripagent.xhsagentyunying.service.TitleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/titles")
@RequiredArgsConstructor
public class TitlesController {

    private static final int CODE_UNAUTHORIZED = 40101;

    private final TitleService titleService;

    @PostMapping
    public ApiResponse<TitleGenerateResponse> generate(@Valid @RequestBody TitleGenerateRequest request) {
        Long userId = requireUserId();
        return ApiResponse.ok(titleService.generate(userId, request));
    }

    private Long requireUserId() {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        }
        return userId;
    }
}
