package com.shortvideoscripagent.xhsagentyunying.ai.agent.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.AgentCard;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.AgentRequest;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.AgentResponse;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.ToolTrace;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ChatMessage;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ChatSession;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ChatMessageMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ChatSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    private static final int CODE_NOT_FOUND = 40402;
    private static final int CODE_FORBIDDEN = 40301;

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final SessionMemoryStore sessionMemoryStore;
    private final ObjectMapper objectMapper;

    private volatile String agentSystemPromptTemplate;

    @Transactional
    public ChatSession createSession(Long userId, String persona, String linkedTaskId, String title) {
        OffsetDateTime now = OffsetDateTime.now();
        ChatSession session = new ChatSession();
        session.setId(generateSessionId());
        session.setUserId(userId);
        session.setPersona(persona == null || persona.isBlank() ? "agency" : persona);
        session.setLinkedTaskId(trimToNull(linkedTaskId));
        session.setTitle(trimToNull(title));
        session.setStatus("active");
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        sessionMapper.insert(session);
        sessionMemoryStore.touchSession(session);
        return session;
    }

    public ChatSession requireOwnedSession(Long userId, String sessionId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(CODE_NOT_FOUND, "chat_session_not_found");
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(CODE_FORBIDDEN, "forbidden");
        }
        return session;
    }

    @Transactional
    public ChatMessage saveUserMessage(ChatSession session, AgentRequest request) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(session.getId());
        message.setRole("user");
        message.setContent(request.content());
        applyMetadata(message, buildUserMetadata(request));
        message.setCreatedAt(OffsetDateTime.now());
        messageMapper.insert(message);
        touchSession(session);
        sessionMemoryStore.cacheContextSnippet(session.getId(), Map.of(
                "role", "user",
                "content", request.content() == null ? "" : request.content()
        ));
        return message;
    }

    @Transactional
    public ChatMessage saveAssistantMessage(
            ChatSession session,
            String content,
            List<AgentCard> cards,
            List<ToolTrace> toolTraces
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (cards != null && !cards.isEmpty()) {
            metadata.put("cards", cards);
        }
        if (toolTraces != null && !toolTraces.isEmpty()) {
            metadata.put("toolTraces", toolTraces);
        }

        ChatMessage message = new ChatMessage();
        message.setSessionId(session.getId());
        message.setRole("assistant");
        message.setContent(content);
        applyMetadata(message, metadata);
        message.setCreatedAt(OffsetDateTime.now());
        messageMapper.insert(message);
        touchSession(session);
        return message;
    }

    @Transactional
    public void saveToolExchange(
            ChatSession session,
            AssistantMessage assistantMessage,
            List<ToolResponseMessage.ToolResponse> toolResponses
    ) {
        if (assistantMessage.hasToolCalls()) {
            ChatMessage assistantRecord = new ChatMessage();
            assistantRecord.setSessionId(session.getId());
            assistantRecord.setRole("assistant");
            assistantRecord.setContent(assistantMessage.getText());
            assistantRecord.setToolCalls(serializeJson(toToolCallMaps(assistantMessage.getToolCalls())));
            assistantRecord.setCreatedAt(OffsetDateTime.now());
            messageMapper.insert(assistantRecord);
        }

        for (ToolResponseMessage.ToolResponse response : toolResponses) {
            ChatMessage toolRecord = new ChatMessage();
            toolRecord.setSessionId(session.getId());
            toolRecord.setRole("tool");
            toolRecord.setContent(response.responseData());
            toolRecord.setToolCallId(response.id());
            toolRecord.setToolName(response.name());
            toolRecord.setCreatedAt(OffsetDateTime.now());
            messageMapper.insert(toolRecord);
        }
        touchSession(session);
    }

    @Transactional
    public void updateLinkedTaskId(ChatSession session, String taskId) {
        if (taskId == null || taskId.isBlank() || taskId.equals(session.getLinkedTaskId())) {
            return;
        }
        session.setLinkedTaskId(taskId);
        session.setUpdatedAt(OffsetDateTime.now());
        sessionMapper.updateById(session);
        sessionMemoryStore.touchSession(session);
    }

    public List<Message> buildMessages(ChatSession session, int maxMessages) {
        List<ChatMessage> records = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, session.getId())
                .orderByAsc(ChatMessage::getId)
                .last("LIMIT " + Math.max(maxMessages, 1)));

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(renderSystemPrompt(session)));

        for (ChatMessage record : records) {
            switch (record.getRole()) {
                case "user" -> messages.add(new UserMessage(formatUserContent(record)));
                case "assistant" -> {
                    if (record.getToolCalls() != null && !record.getToolCalls().isBlank()) {
                        List<AssistantMessage.ToolCall> toolCalls = parseToolCalls(record.getToolCalls());
                        messages.add(AssistantMessage.builder()
                                .content(record.getContent() == null ? "" : record.getContent())
                                .toolCalls(toolCalls)
                                .build());
                    } else {
                        messages.add(new AssistantMessage(record.getContent() == null ? "" : record.getContent()));
                    }
                }
                case "tool" -> messages.add(ToolResponseMessage.builder()
                        .responses(List.of(new ToolResponseMessage.ToolResponse(
                                record.getToolCallId(),
                                record.getToolName(),
                                record.getContent() == null ? "" : record.getContent()
                        )))
                        .build());
                default -> {
                }
            }
        }
        return messages;
    }

    public long countMessages(String sessionId) {
        return messageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId));
    }

    public List<ChatMessage> listMessages(String sessionId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;
        return messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getId)
                .last("LIMIT " + safeSize + " OFFSET " + offset));
    }

    public AgentResponse toAgentResponse(ChatMessage message) {
        Map<String, Object> metadata = parseJsonMap(message.getMetadata());
        return AgentResponse.builder()
                .messageId(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .cards(castCards(metadata.get("cards")))
                .toolTraces(castToolTraces(metadata.get("toolTraces")))
                .build();
    }

    public String renderSystemPrompt(ChatSession session) {
        return agentSystemPromptTemplate()
                .replace("{{persona}}", personaLabel(session.getPersona()))
                .replace("{{linkedTaskId}}", session.getLinkedTaskId() == null ? "无" : session.getLinkedTaskId());
    }

    private String agentSystemPromptTemplate() {
        if (agentSystemPromptTemplate != null) {
            return agentSystemPromptTemplate;
        }
        synchronized (this) {
            if (agentSystemPromptTemplate != null) {
                return agentSystemPromptTemplate;
            }
            try {
                ClassPathResource resource = new ClassPathResource("prompts/agent-system.st");
                agentSystemPromptTemplate = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                agentSystemPromptTemplate = "你是小红书全平台内容的 AI 运营助手。";
            }
            return agentSystemPromptTemplate;
        }
    }

    private void touchSession(ChatSession session) {
        session.setUpdatedAt(OffsetDateTime.now());
        sessionMapper.updateById(session);
        sessionMemoryStore.touchSession(session);
    }

    private Map<String, Object> buildUserMetadata(AgentRequest request) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (request.attachments() != null && !request.attachments().isEmpty()) {
            metadata.put("attachments", request.attachments());
        }
        return metadata;
    }

    private String formatUserContent(ChatMessage record) {
        StringBuilder sb = new StringBuilder(record.getContent() == null ? "" : record.getContent());
        Map<String, Object> metadata = parseJsonMap(record.getMetadata());
        Object attachments = metadata.get("attachments");
        if (attachments instanceof Map<?, ?> map) {
            appendAttachment(sb, "标题", map.get("title"));
            appendAttachment(sb, "正文", map.get("body"));
            appendAttachment(sb, "封面", map.get("coverImageUrl"));
        }
        return sb.toString();
    }

    private void appendAttachment(StringBuilder sb, String label, Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return;
        }
        sb.append("\n\n【").append(label).append("】\n").append(value);
    }

    private List<Map<String, Object>> toToolCallMaps(List<AssistantMessage.ToolCall> toolCalls) {
        if (toolCalls == null) {
            return List.of();
        }
        return toolCalls.stream().map(call -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", call.id());
            map.put("type", call.type());
            map.put("name", call.name());
            map.put("arguments", call.arguments());
            return map;
        }).toList();
    }

    private List<AssistantMessage.ToolCall> parseToolCalls(String json) {
        try {
            List<Map<String, Object>> maps = objectMapper.readValue(json, new TypeReference<>() {
            });
            return maps.stream()
                    .map(map -> new AssistantMessage.ToolCall(
                            String.valueOf(map.get("id")),
                            String.valueOf(map.getOrDefault("type", "function")),
                            String.valueOf(map.get("name")),
                            String.valueOf(map.getOrDefault("arguments", "{}"))
                    ))
                    .toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<AgentCard> castCards(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<AgentCard> cards = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                cards.add(AgentCard.builder()
                        .type(String.valueOf(map.get("type")))
                        .taskId(map.get("taskId") == null ? null : String.valueOf(map.get("taskId")))
                        .payload((Map<String, Object>) map.get("payload"))
                        .build());
            }
        }
        return cards;
    }

    @SuppressWarnings("unchecked")
    private List<ToolTrace> castToolTraces(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<ToolTrace> traces = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                traces.add(ToolTrace.builder()
                        .tool(String.valueOf(map.get("tool")))
                        .success(Boolean.TRUE.equals(map.get("success")))
                        .latencyMs(map.get("latencyMs") instanceof Number n ? n.intValue() : 0)
                        .error(map.get("error") == null ? null : String.valueOf(map.get("error")))
                        .build());
            }
        }
        return traces;
    }

    private Map<String, Object> parseJsonMap(String json) {
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

    private void applyMetadata(ChatMessage message, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        message.setMetadata(serializeJson(metadata));
    }

    private String serializeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private static String personaLabel(String persona) {
        return switch (persona == null ? "agency" : persona) {
            case "mentor" -> "导师 IP";
            case "senior" -> "学长学姐";
            default -> "机构号";
        };
    }

    private static String generateSessionId() {
        return "sess_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
