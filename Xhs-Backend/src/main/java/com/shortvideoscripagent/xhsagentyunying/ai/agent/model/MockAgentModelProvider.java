package com.shortvideoscripagent.xhsagentyunying.ai.agent.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 无 DashScope 调用时的规则化 Agent：根据用户意图返回 tool_calls 或最终文本。
 */
@Component
public class MockAgentModelProvider implements AgentModelProvider {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public AgentLlmResponse chatWithTools(List<Message> messages, List<ToolCallback> toolCallbacks, int timeoutSeconds) {
        String lastUser = extractLastUserMessage(messages);
        boolean afterTools = hasRecentToolMessages(messages);

        if (!afterTools) {
            List<AssistantMessage.ToolCall> toolCalls = planInitialTools(lastUser, messages);
            if (!toolCalls.isEmpty()) {
                return new AgentLlmResponse("", toolCalls, true);
            }
        }

        if (shouldGenerateTitles(lastUser) && !alreadyCalled(messages, "generate_titles")) {
            return new AgentLlmResponse("", List.of(buildToolCall(
                    "generate_titles",
                    Map.of("goal", "high_ctr", "count", 8)
            )), true);
        }

        if (shouldScanCompliance(lastUser) && !alreadyCalled(messages, "scan_compliance")) {
            return new AgentLlmResponse("", List.of(buildToolCall("scan_compliance", Map.of())), true);
        }

        if (shouldListHistory(lastUser) && !alreadyCalled(messages, "list_recent_analyses")) {
            return new AgentLlmResponse("", List.of(buildToolCall(
                    "list_recent_analyses",
                    Map.of("limit", 5)
            )), true);
        }

        if (shouldOptimizeDraft(lastUser) && !alreadyCalled(messages, "optimize_draft")) {
            return new AgentLlmResponse("", List.of(buildToolCall("optimize_draft", Map.of())), true);
        }

        if (shouldHotTopics(lastUser) && !alreadyCalled(messages, "get_hot_topics")) {
            return new AgentLlmResponse("", List.of(buildToolCall("get_hot_topics", Map.of("limit", 5))), true);
        }

        if (shouldWebSearch(lastUser) && !alreadyCalled(messages, "web_search")) {
            return new AgentLlmResponse("", List.of(buildToolCall(
                    "web_search",
                    Map.of("query", extractSearchQuery(lastUser), "maxResults", 5)
            )), true);
        }

        return new AgentLlmResponse(buildFinalAnswer(lastUser, messages), List.of(), false);
    }

    private List<AssistantMessage.ToolCall> planInitialTools(String lastUser, List<Message> messages) {
        List<AssistantMessage.ToolCall> calls = new ArrayList<>();
        Map<String, Object> attachments = extractAttachmentsFromMessages(messages);

        String title = stringVal(attachments.get("title"));
        if (title.isBlank()) {
            title = extractTaggedContent(lastUser, "标题");
        }
        String body = stringVal(attachments.get("body"));
        if (body.isBlank()) {
            body = extractTaggedContent(lastUser, "正文");
        }
        String cover = stringVal(attachments.get("coverImageUrl"));
        if (cover.isBlank()) {
            cover = extractTaggedContent(lastUser, "封面");
        }
        boolean hasDraft = !title.isBlank() || !body.isBlank();
        boolean wantsAnalysis = lastUser.contains("分析") || hasDraft;

        if (wantsAnalysis && hasDraft) {
            calls.add(buildToolCall("search_kb", Map.of(
                    "query", buildQuery(title, body),
                    "topK", 3
            )));

            Map<String, Object> analyzeArgs = new LinkedHashMap<>();
            if (!title.isBlank()) {
                analyzeArgs.put("title", title);
            }
            if (!body.isBlank()) {
                analyzeArgs.put("body", body);
            }
            if (!cover.isBlank()) {
                analyzeArgs.put("coverImageUrl", cover);
            }
            analyzeArgs.put("scenario", "draft");
            calls.add(buildToolCall("analyze_content", analyzeArgs));

            if (!cover.isBlank()) {
                Map<String, Object> coverArgs = new LinkedHashMap<>();
                coverArgs.put("coverImageUrl", cover);
                if (!title.isBlank()) {
                    coverArgs.put("title", title);
                }
                if (!body.isBlank()) {
                    coverArgs.put("body", body);
                }
                calls.add(buildToolCall("analyze_cover", coverArgs));
            }
        } else if (lastUser.contains("报告") || lastUser.contains("评分")) {
            calls.add(buildToolCall("list_recent_analyses", Map.of("limit", 1)));
        }

        return calls;
    }

    private String buildFinalAnswer(String lastUser, List<Message> messages) {
        if (lastUser.contains("标题")) {
            return "已为你生成标题候选，请查看标题卡片并按移动端展示效果选用。";
        }
        if (lastUser.contains("违规") || lastUser.contains("合规")) {
            return "合规扫描已完成，请查看 warnings 列表并按建议调整表述。";
        }
        if (lastUser.contains("历史") || lastUser.contains("上周")) {
            return "这是你最近的分析任务列表，点击 taskId 可查看完整报告。";
        }
        if (lastUser.contains("优化稿") || lastUser.contains("优化")) {
            return "优化稿已生成，请查看卡片中的标题与正文，可按需复制编辑。";
        }
        if (lastUser.contains("热点") || lastUser.contains("选题")) {
            return "已为你整理当前热门选题方向，请查看选题卡片。";
        }
        if (lastUser.contains("搜索") || lastUser.contains("联网")) {
            return "联网搜索已完成，请查看搜索结果摘要。";
        }
        if (containsToolResult(messages, "analyze_content")) {
            return "分析已完成。五维评分与主要问题已整理在报告卡片中；如需标题变体，可以直接说「给我 8 个高点击标题」。";
        }
        return "我是小红书留学生求职运营助手。你可以粘贴标题和正文让我分析，或者说「生成标题」「扫描合规」。";
    }

    private boolean shouldGenerateTitles(String lastUser) {
        return lastUser.contains("标题");
    }

    private boolean shouldScanCompliance(String lastUser) {
        return lastUser.contains("违规") || lastUser.contains("合规");
    }

    private boolean shouldListHistory(String lastUser) {
        return lastUser.contains("历史") || lastUser.contains("上周") || lastUser.contains("最近分析");
    }

    private boolean shouldOptimizeDraft(String lastUser) {
        return lastUser.contains("优化稿") || (lastUser.contains("优化") && lastUser.contains("正文"));
    }

    private boolean shouldHotTopics(String lastUser) {
        return lastUser.contains("热点") || lastUser.contains("选题");
    }

    private boolean shouldWebSearch(String lastUser) {
        return lastUser.contains("搜索") || lastUser.contains("联网") || lastUser.contains("政策");
    }

    private String extractSearchQuery(String lastUser) {
        if (lastUser.contains("秋招")) {
            return "2026留学生秋招趋势";
        }
        if (lastUser.contains("H1B") || lastUser.contains("签证")) {
            return "H1B 签证政策 2026";
        }
        return "留学生求职 小红书 热点";
    }

    private boolean alreadyCalled(List<Message> messages, String toolName) {
        for (Message message : messages) {
            if (message instanceof org.springframework.ai.chat.messages.ToolResponseMessage toolMessage) {
                for (ToolResponseMessage.ToolResponse response : toolMessage.getResponses()) {
                    if (toolName.equals(response.name())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean containsToolResult(List<Message> messages, String toolName) {
        return alreadyCalled(messages, toolName);
    }

    private boolean hasRecentToolMessages(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message instanceof UserMessage) {
                return false;
            }
            if (message instanceof org.springframework.ai.chat.messages.ToolResponseMessage) {
                return true;
            }
        }
        return false;
    }

    private String extractLastUserMessage(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage userMessage) {
                return userMessage.getText() == null ? "" : userMessage.getText();
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractAttachmentsFromMessages(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage userMessage) {
                return parseTaggedAttachments(userMessage.getText());
            }
        }
        return Map.of();
    }

    private Map<String, Object> parseTaggedAttachments(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        Map<String, Object> attachments = new LinkedHashMap<>();
        putTagged(attachments, "title", extractTaggedContent(text, "标题"));
        putTagged(attachments, "body", extractTaggedContent(text, "正文"));
        putTagged(attachments, "coverImageUrl", extractTaggedContent(text, "封面"));
        return attachments;
    }

    private void putTagged(Map<String, Object> attachments, String key, String value) {
        if (!value.isBlank()) {
            attachments.put(key, value);
        }
    }

    private String extractTaggedContent(String text, String label) {
        String marker = "【" + label + "】";
        int start = text.indexOf(marker);
        if (start < 0) {
            return "";
        }
        start += marker.length();
        int next = text.indexOf("\n\n【", start);
        if (next < 0) {
            return text.substring(start).trim();
        }
        return text.substring(start, next).trim();
    }

    private AssistantMessage.ToolCall buildToolCall(String name, Map<String, Object> args) {
        try {
            String id = "call_" + name + "_" + System.nanoTime();
            return new AssistantMessage.ToolCall(id, "function", name, objectMapper.writeValueAsString(args));
        } catch (Exception ex) {
            return new AssistantMessage.ToolCall("call_err", "function", name, "{}");
        }
    }

    private static String buildQuery(String title, String body) {
        String combined = (title + " " + body).trim();
        return combined.length() > 500 ? combined.substring(0, 500) : combined;
    }

    private static String stringVal(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
