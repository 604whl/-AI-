package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.PaginatedResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.chat.ChatMessageItemResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.chat.ChatMessageResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.chat.ChatMessageSendRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.chat.ChatSessionCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.chat.ChatSessionResponse;
import com.shortvideoscripagent.xhsagentyunying.service.ChatAppService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final int CODE_UNAUTHORIZED = 40101;

    private final ChatAppService chatAppService;

    @PostMapping("/sessions")
    public ApiResponse<ChatSessionResponse> createSession(@Valid @RequestBody ChatSessionCreateRequest request) {
        Long userId = requireUserId();
        return ApiResponse.ok(chatAppService.createSession(userId, request));
    }

    @GetMapping("/sessions")
    public ApiResponse<PaginatedResponse<ChatSessionResponse>> listSessions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = requireUserId();
        return ApiResponse.ok(chatAppService.listSessions(userId, page, size));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ApiResponse<ChatMessageResponse> sendMessage(
            @PathVariable String sessionId,
            @Valid @RequestBody ChatMessageSendRequest request
    ) {
        Long userId = requireUserId();
        return ApiResponse.ok(chatAppService.sendMessage(userId, sessionId, request));
    }

    /**
     * SSE 流式对话：推送 Agent 步骤、工具调用进度与回复正文增量。
     */
    @PostMapping(value = "/sessions/{sessionId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessageStream(
            @PathVariable String sessionId,
            @Valid @RequestBody ChatMessageSendRequest request
    ) {
        Long userId = requireUserId();
        return chatAppService.sendMessageStream(userId, sessionId, request);
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<PaginatedResponse<ChatMessageItemResponse>> listMessages(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Long userId = requireUserId();
        return ApiResponse.ok(chatAppService.listMessages(userId, sessionId, page, size));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> archiveSession(@PathVariable String sessionId) {
        Long userId = requireUserId();
        chatAppService.archiveSession(userId, sessionId);
        return ApiResponse.ok(null);
    }

    private Long requireUserId() {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        }
        return userId;
    }
}
