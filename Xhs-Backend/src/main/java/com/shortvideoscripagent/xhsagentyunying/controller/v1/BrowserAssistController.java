package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.browser.BrowserSessionResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.browser.CreateSessionRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.browser.FillSessionRequest;
import com.shortvideoscripagent.xhsagentyunying.service.BrowserAssistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/browser-assist")
@RequiredArgsConstructor
public class BrowserAssistController {

    private static final int CODE_UNAUTHORIZED = 40101;

    private final BrowserAssistService browserAssistService;

    @PostMapping("/sessions")
    public ApiResponse<BrowserSessionResponse> createSession(@Valid @RequestBody CreateSessionRequest req) {
        Long userId = requireUserId();
        return ApiResponse.ok(browserAssistService.createSession(userId, req));
    }

    @GetMapping("/sessions/{id}")
    public ApiResponse<BrowserSessionResponse> getSession(@PathVariable String id) {
        Long userId = requireUserId();
        UUID sessionId = UUID.fromString(id);
        return ApiResponse.ok(browserAssistService.getSession(userId, sessionId));
    }

    @PostMapping("/fill")
    public ApiResponse<BrowserSessionResponse> fillSession(@Valid @RequestBody FillSessionRequest req) {
        Long userId = requireUserId();
        return ApiResponse.ok(browserAssistService.fillSession(userId, req));
    }

    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Void> closeSession(@PathVariable String id) {
        Long userId = requireUserId();
        UUID sessionId = UUID.fromString(id);
        browserAssistService.closeSession(userId, sessionId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/sessions")
    public ApiResponse<List<BrowserSessionResponse>> listSessions() {
        Long userId = requireUserId();
        return ApiResponse.ok(browserAssistService.listSessions(userId));
    }

    private Long requireUserId() {
        Long uid = RequestContext.getUserId();
        if (uid == null) {
            throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        }
        return uid;
    }
}