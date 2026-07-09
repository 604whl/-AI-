package com.shortvideoscripagent.xhsagentyunying.ai.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class OpenAiCompatibleHttpModelProvider implements ModelProvider {

    private static final int CODE_AI_UNAVAILABLE = 50002;
    private static final int CODE_AI_RESPONSE_INVALID = 50003;
    private static final int CODE_AI_QUOTA_EXCEEDED = 42902;

    private final String id;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleHttpModelProvider(
            String id,
            String apiKey,
            String baseUrl,
            String model,
            ObjectMapper objectMapper
    ) {
        this.id = id;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.model = model == null ? "" : model.trim();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        if (apiKey.isBlank() || baseUrl.isBlank() || model.isBlank()) {
            throw new BusinessException(CODE_AI_UNAVAILABLE, id + "_provider_not_configured");
        }
        try {
            Map<String, Object> payload = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt == null ? "" : systemPrompt),
                            Map.of("role", "user", "content", userPrompt == null ? "" : userPrompt)
                    )
            );
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(chatCompletionsUrl()))
                    .timeout(Duration.ofSeconds(70))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(mapStatusCode(response.statusCode()), id + "_api_error: " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new BusinessException(CODE_AI_RESPONSE_INVALID, id + "_response_invalid");
            }
            return content;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(CODE_AI_UNAVAILABLE, id + "_service_error");
        }
    }

    @Override
    public boolean supportsVision() {
        return false;
    }

    private String chatCompletionsUrl() {
        if (baseUrl.endsWith("/chat/completions")) {
            return baseUrl;
        }
        return baseUrl + "/chat/completions";
    }

    private static String normalizeBaseUrl(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static int mapStatusCode(int statusCode) {
        if (statusCode == 429) {
            return CODE_AI_QUOTA_EXCEEDED;
        }
        return CODE_AI_UNAVAILABLE;
    }
}
