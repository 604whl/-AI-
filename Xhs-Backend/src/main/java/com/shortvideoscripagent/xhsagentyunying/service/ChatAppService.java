package com.shortvideoscripagent.xhsagentyunying.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.AgentOrchestrator;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.AgentRequest;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.AgentResponse;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.AgentRuntimePolicy;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.memory.ChatHistoryService;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ChatMessage;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ChatSession;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ChatSessionMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.PaginatedResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.chat.ChatAgentCardDto;
import com.shortvideoscripagent.xhsagentyunying.dto.chat.ChatMessageItemResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.chat.ChatMessageResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.chat.ChatMessageSendRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.chat.ChatSessionCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.chat.ChatSessionResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.chat.ChatToolTraceDto;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.service.chat.ChatSseWriter;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
public class ChatAppService {

    private static final int CODE_AGENT_DISABLED = 40006;

    private final AgentRuntimePolicy agentRuntimePolicy;
    private final ChatHistoryService chatHistoryService;
    private final ChatSessionMapper chatSessionMapper;
    private final AgentOrchestrator agentOrchestrator;
    private final UserQuotaService userQuotaService;
    private final ObjectMapper objectMapper;

    @Resource(name = "chatStreamExecutor")
    private Executor chatStreamExecutor;

    @Transactional
    public ChatSessionResponse createSession(Long userId, ChatSessionCreateRequest request) {
        assertAgentEnabled();
        ChatSession session = chatHistoryService.createSession(
                userId,
                request.getPersona(),
                request.getLinkedTaskId(),
                request.getTitle()
        );
        return toSessionResponse(session);
    }

    public PaginatedResponse<ChatSessionResponse> listSessions(Long userId, int page, int size) {
        assertAgentEnabled();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        int offset = (safePage - 1) * safeSize;

        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .eq(ChatSession::getStatus, "active")
                .orderByDesc(ChatSession::getUpdatedAt)
                .last("LIMIT " + safeSize + " OFFSET " + offset);

        List<ChatSession> sessions = chatSessionMapper.selectList(wrapper);
        Long total = chatSessionMapper.selectCount(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .eq(ChatSession::getStatus, "active"));

        List<ChatSessionResponse> items = sessions.stream().map(this::toSessionResponse).toList();
        return new PaginatedResponse<>(items, total == null ? 0 : total, safePage, safeSize);
    }

    public ChatMessageResponse sendMessage(Long userId, String sessionId, ChatMessageSendRequest request) {
        assertAgentEnabled();
        userQuotaService.consumeAgentMessageQuota(userId, sessionId);

        ChatSession session = chatHistoryService.requireOwnedSession(userId, sessionId);
        AgentRequest agentRequest = AgentRequest.builder()
                .userId(userId)
                .sessionId(sessionId)
                .content(request.getContent())
                .attachments(request.getAttachments())
                .build();

        AgentResponse response = agentOrchestrator.run(session, agentRequest);
        return toMessageResponse(response);
    }

    public SseEmitter sendMessageStream(Long userId, String sessionId, ChatMessageSendRequest request) {
        assertAgentEnabled();
        userQuotaService.consumeAgentMessageQuota(userId, sessionId);

        ChatSession session = chatHistoryService.requireOwnedSession(userId, sessionId);
        AgentRequest agentRequest = AgentRequest.builder()
                .userId(userId)
                .sessionId(sessionId)
                .content(request.getContent())
                .attachments(request.getAttachments())
                .build();

        long timeoutMs = (agentRuntimePolicy.totalTimeoutSeconds() + 30L) * 1000L;
        SseEmitter emitter = new SseEmitter(timeoutMs);

        Long capturedUserId = userId;
        String requestId = RequestContext.getRequestId();

        chatStreamExecutor.execute(() -> {
            ChatSseWriter writer = new ChatSseWriter(emitter, objectMapper, this::toMessageResponse);
            try {
                RequestContext.setUserId(capturedUserId);
                if (requestId != null) {
                    RequestContext.setRequestId(requestId);
                }
                agentOrchestrator.run(session, agentRequest, writer);
                emitter.complete();
            } catch (BusinessException ex) {
                writer.sendBusinessError(ex);
                emitter.complete();
            } catch (Exception ex) {
                writer.sendGenericError(ex.getMessage());
                emitter.completeWithError(ex);
            } finally {
                RequestContext.clear();
            }
        });

        return emitter;
    }

    public PaginatedResponse<ChatMessageItemResponse> listMessages(Long userId, String sessionId, int page, int size) {
        assertAgentEnabled();
        chatHistoryService.requireOwnedSession(userId, sessionId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);

        List<ChatMessage> messages = chatHistoryService.listMessages(sessionId, safePage, safeSize);
        long total = chatHistoryService.countMessages(sessionId);

        List<ChatMessageItemResponse> items = messages.stream().map(this::toMessageItem).toList();
        return new PaginatedResponse<>(items, total, safePage, safeSize);
    }

    @Transactional
    public void archiveSession(Long userId, String sessionId) {
        assertAgentEnabled();
        ChatSession session = chatHistoryService.requireOwnedSession(userId, sessionId);
        session.setStatus("archived");
        chatSessionMapper.updateById(session);
    }

    private void assertAgentEnabled() {
        if (!agentRuntimePolicy.isEnabled()) {
            throw new BusinessException(CODE_AGENT_DISABLED, "agent_disabled");
        }
    }

    private ChatSessionResponse toSessionResponse(ChatSession session) {
        return ChatSessionResponse.builder()
                .sessionId(session.getId())
                .title(session.getTitle())
                .persona(session.getPersona())
                .linkedTaskId(session.getLinkedTaskId())
                .status(session.getStatus())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }

    private ChatMessageResponse toMessageResponse(AgentResponse response) {
        return ChatMessageResponse.builder()
                .messageId(response.messageId())
                .role(response.role())
                .content(response.content())
                .cards(response.cards() == null ? List.of() : response.cards().stream()
                        .map(card -> ChatAgentCardDto.builder()
                                .type(card.type())
                                .taskId(card.taskId())
                                .payload(card.payload())
                                .build())
                        .toList())
                .toolTraces(response.toolTraces() == null ? List.of() : response.toolTraces().stream()
                        .map(trace -> ChatToolTraceDto.builder()
                                .tool(trace.tool())
                                .success(trace.success())
                                .latencyMs(trace.latencyMs())
                                .error(trace.error())
                                .build())
                        .toList())
                .build();
    }

    private ChatMessageItemResponse toMessageItem(ChatMessage message) {
        return ChatMessageItemResponse.builder()
                .id(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .metadata(parseMetadata(message.getMetadata()))
                .createdAt(message.getCreatedAt())
                .build();
    }

    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
