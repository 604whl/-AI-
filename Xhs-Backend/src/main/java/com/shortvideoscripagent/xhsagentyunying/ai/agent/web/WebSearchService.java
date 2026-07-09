package com.shortvideoscripagent.xhsagentyunying.ai.agent.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.ai.AiRuntimePolicy;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.config.AppAgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSearchService {

    private static final int CODE_WEB_SEARCH_UNAVAILABLE = 50007;

    private final AppAgentProperties appAgentProperties;
    private final AiRuntimePolicy aiRuntimePolicy;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public boolean isConfigured() {
        return !aiRuntimePolicy.useMockResponses()
                && "tavily".equalsIgnoreCase(appAgentProperties.getWebSearch().getProvider())
                && !appAgentProperties.getWebSearch().getApiKey().isBlank();
    }

    public List<Map<String, Object>> search(String query, int maxResults) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int limit = Math.min(Math.max(maxResults, 1), 10);

        if (aiRuntimePolicy.useMockResponses()) {
            throw new BusinessException(CODE_WEB_SEARCH_UNAVAILABLE, "web_search_mock_mode_enabled");
        }
        if (appAgentProperties.getWebSearch().getApiKey().isBlank()) {
            throw new BusinessException(CODE_WEB_SEARCH_UNAVAILABLE, "web_search_not_configured");
        }

        String provider = appAgentProperties.getWebSearch().getProvider();
        if ("tavily".equalsIgnoreCase(provider)) {
            return searchTavily(query, limit);
        }
        throw new BusinessException(CODE_WEB_SEARCH_UNAVAILABLE, "web_search_provider_unsupported");
    }

    private List<Map<String, Object>> searchTavily(String query, int limit) {
        try {
            String apiKey = appAgentProperties.getWebSearch().getApiKey();
            String body = objectMapper.writeValueAsString(Map.of(
                    "api_key", apiKey,
                    "query", query,
                    "max_results", limit,
                    "search_depth", "basic"
            ));
            String raw = restClient.post()
                    .uri("https://api.tavily.com/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(raw);
            JsonNode results = root.path("results");
            List<Map<String, Object>> items = new ArrayList<>();
            if (results.isArray()) {
                for (JsonNode node : results) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("title", node.path("title").asText(""));
                    item.put("url", node.path("url").asText(""));
                    item.put("snippet", node.path("content").asText(""));
                    items.add(item);
                    if (items.size() >= limit) {
                        break;
                    }
                }
            }
            if (items.isEmpty()) {
                throw new BusinessException(CODE_WEB_SEARCH_UNAVAILABLE, "web_search_empty_result");
            }
            return items;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Tavily search failed: {}", ex.getMessage());
            throw new BusinessException(CODE_WEB_SEARCH_UNAVAILABLE, "web_search_failed");
        }
    }
}
