package com.shortvideoscripagent.xhsagentyunying.service.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.AgentResponse;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.AgentStreamListener;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.chat.ChatMessageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

@Slf4j
public class ChatSseWriter implements AgentStreamListener {

    private final SseEmitter emitter;
    private final ObjectMapper objectMapper;
    private final Function<AgentResponse, ChatMessageResponse> responseMapper;

    public ChatSseWriter(
            SseEmitter emitter,
            ObjectMapper objectMapper,
            Function<AgentResponse, ChatMessageResponse> responseMapper
    ) {
        this.emitter = emitter;
        this.objectMapper = objectMapper;
        this.responseMapper = responseMapper;
    }

    @Override
    public void onStepStart(int step, int maxSteps) {
        send("step_start", Map.of("step", step, "maxSteps", maxSteps));
    }

    @Override
    public void onToolStart(String tool, int step) {
        send("tool_start", Map.of("tool", tool, "step", step));
    }

    @Override
    public void onToolEnd(String tool, boolean success, long latencyMs, String error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tool", tool);
        payload.put("success", success);
        payload.put("latencyMs", latencyMs);
        if (error != null) {
            payload.put("error", error);
        }
        send("tool_end", payload);
    }

    @Override
    public void onDelta(String contentChunk) {
        send("delta", Map.of("content", contentChunk));
    }

    @Override
    public void onDone(AgentResponse response) {
        send("done", responseMapper.apply(response));
    }

    @Override
    public void onError(int code, String message) {
        send("error", Map.of("code", code, "message", message));
    }

    public void sendBusinessError(BusinessException ex) {
        onError(ex.getCode(), ex.getMessage());
    }

    public void sendGenericError(String message) {
        onError(50000, message == null ? "internal_error" : message);
    }

    private void send(String event, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event)
                    .data(objectMapper.writeValueAsString(data)));
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize SSE payload for event {}", event, ex);
        } catch (IOException ex) {
            log.debug("SSE client disconnected during event {}", event);
        }
    }
}
