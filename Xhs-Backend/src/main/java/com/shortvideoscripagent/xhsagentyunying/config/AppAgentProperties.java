package com.shortvideoscripagent.xhsagentyunying.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.agent")
public class AppAgentProperties {

    private boolean enabled = false;
    private boolean mockEnabled = false;
    private int maxSteps = 8;
    private int totalTimeoutSeconds = 120;
    private int sessionTtlHours = 24;
    private int maxContextMessages = 20;
    private int dailyMessageQuota = 20;
    private Tools tools = new Tools();
    private WebSearch webSearch = new WebSearch();
    private Mcp mcp = new Mcp();

    @Data
    public static class Tools {
        private boolean searchKb = true;
        private boolean analyzeContent = true;
        private boolean generateTitles = true;
        private boolean scanCompliance = true;
        private boolean getAnalysisReport = true;
        private boolean listRecentAnalyses = true;
        private boolean analyzeCover = true;
        private boolean optimizeDraft = true;
        private boolean webSearch = false;
        private boolean fetchUrl = false;
        private boolean getHotTopics = true;
        private boolean getIndustryCalendar = true;
        private boolean getUserProfile = true;
    }

    @Data
    public static class WebSearch {
        private String provider = "tavily";
        private String apiKey = "";
        private int dailyQuotaPerUser = 10;
    }

    /**
     * MCP（Model Context Protocol）Server 配置。
     * <p>
     * 启用后，外部 MCP 客户端（如 Cursor、Claude Desktop、Spring AI MCP Client）
     * 可通过 HTTP Streamable 协议调用本应用暴露的 Agent 工具。
     * </p>
     */
    @Data
    public static class Mcp {
        /** 是否启用 MCP Server 端点（默认关闭，需显式开启） */
        private boolean enabled = false;
        /**
         * 可选的服务级 API Key，用于无 JWT 的 MCP 客户端。
         * 与 {@code X-Mcp-User-Id} 请求头配合，仅建议在 local/dev 使用。
         */
        private String apiKey = "";
        /**
         * 通过 MCP 对外暴露的工具名列表（逗号分隔或 YAML 数组）。
         * 默认暴露 search_kb 与 web_search（见 {@link #getExposedToolNames()}）。
         */
        private java.util.List<String> exposedTools = java.util.List.of("search_kb", "web_search");
        /** MCP 虚拟会话 ID 前缀，用于 agent_tool_log 与配额追踪 */
        private String sessionIdPrefix = "mcp_";
    }

    /**
     * 返回经过去重、去空白后的 MCP 暴露工具名列表。
     */
    public java.util.List<String> getExposedToolNames() {
        if (mcp.exposedTools == null || mcp.exposedTools.isEmpty()) {
            return java.util.List.of("search_kb", "web_search");
        }
        return mcp.exposedTools.stream()
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .distinct()
                .toList();
    }

    public boolean isToolEnabled(String toolName) {
        return switch (toolName) {
            case "search_kb" -> tools.isSearchKb();
            case "analyze_content" -> tools.isAnalyzeContent();
            case "generate_titles" -> tools.isGenerateTitles();
            case "scan_compliance" -> tools.isScanCompliance();
            case "get_analysis_report" -> tools.isGetAnalysisReport();
            case "list_recent_analyses" -> tools.isListRecentAnalyses();
            case "analyze_cover" -> tools.isAnalyzeCover();
            case "optimize_draft" -> tools.isOptimizeDraft();
            case "web_search" -> tools.isWebSearch();
            case "fetch_url" -> tools.isFetchUrl();
            case "get_hot_topics" -> tools.isGetHotTopics();
            case "get_industry_calendar" -> tools.isGetIndustryCalendar();
            case "get_user_profile" -> tools.isGetUserProfile();
            default -> false;
        };
    }
}
