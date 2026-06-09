package com.shortvideoscripagent.xhsagentyunying.ai.agent.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.ai.AiRuntimePolicy;
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

    private final AppAgentProperties appAgentProperties;
    private final AiRuntimePolicy aiRuntimePolicy;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public List<Map<String, Object>> search(String query, int maxResults) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int limit = Math.min(Math.max(maxResults, 1), 10);

        if (aiRuntimePolicy.useMockResponses() || appAgentProperties.getWebSearch().getApiKey().isBlank()) {
            return mockResults(query, limit);
        }

        String provider = appAgentProperties.getWebSearch().getProvider();
        if ("tavily".equalsIgnoreCase(provider)) {
            return searchTavily(query, limit);
        }
        return mockResults(query, limit);
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
            return items.isEmpty() ? mockResults(query, limit) : items;
        } catch (Exception ex) {
            log.warn("Tavily search failed: {}", ex.getMessage());
            return mockResults(query, limit);
        }
    }

    private List<Map<String, Object>> mockResults(String query, int limit) {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(Map.of(
                "title", "2026 小红书内容趋势解读（Mock）",
                "url", "https://example.com/mock-1",
                "snippet", "检索词「" + query + "」相关：本地生活、知识干货与穿搭赛道互动率持续走高。"
        ));
        if (limit > 1) {
            items.add(Map.of(
                    "title", "小红书热门选题与流量节奏（Mock）",
                    "url", "https://example.com/mock-2",
                    "snippet", "Mock 联网结果：清单合集与结果前置结构在各品类中 CTR 表现稳定。"
            ));
        }
        return items.subList(0, Math.min(limit, items.size()));
    }
}
