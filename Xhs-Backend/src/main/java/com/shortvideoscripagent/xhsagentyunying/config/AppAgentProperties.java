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
