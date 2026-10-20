package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.comment.CommentGenerateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.comment.CommentGenerateResponse;
import com.shortvideoscripagent.xhsagentyunying.service.CommentGenerateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评论生成 REST 入口。
 * <p>
 * 路径：POST /api/v1/comments/generate
 */
@RestController
@RequestMapping("/api/v1/comments")
@RequiredArgsConstructor
public class CommentGenerateController {

    private static final int CODE_UNAUTHORIZED = 40101;

    private final CommentGenerateService commentGenerateService;

    @PostMapping("/generate")
    public ApiResponse<CommentGenerateResponse> generate(@Valid @RequestBody CommentGenerateRequest request) {
        Long userId = requireUserId();
        return ApiResponse.ok(commentGenerateService.generate(request));
    }

    private Long requireUserId() {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        }
        return userId;
    }
}