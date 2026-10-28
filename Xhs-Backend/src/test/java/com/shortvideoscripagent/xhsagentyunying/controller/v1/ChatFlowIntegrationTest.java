package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.agent.enabled=true",
        "app.agent.mock-enabled=true",
        "app.ai.mock-enabled=true"
})
class ChatFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createSessionAndSendMessage_returnsAssistantWithToolTraces() throws Exception {
        String email = "chat-test-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                + "@example.com";
        // /v1/auth/register 按 IP 限流 3 次/小时。测试共享同一个 remoteAddr，
        // 重复运行会互相污染（表现为随机 429）。这里为每个用例分配唯一的伪造 IP，
        // 使用限流拦截器本身支持的 X-Forwarded-For 头，使用例之间完全隔离。
        String clientIp = "10.0.0."
                + (UUID.randomUUID().getLeastSignificantBits() & 0xFF);

        MvcResult authResult = mockMvc.perform(post("/v1/auth/register")
                        .header("X-Forwarded-For", clientIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Test1234","displayName":"Chat Tester"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        String token = objectMapper.readTree(authResult.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
        assertFalse(token.isBlank());

        // 多租户隔离：WorkspaceInterceptor 对非豁免路径强制要求 X-Workspace-Id，
        // 缺失时返回 400 workspace_id_required。注册时后端已自动创建默认工作区，
        // 这里从注册响应里取出来用于后续请求。
        String workspaceId = objectMapper.readTree(authResult.getResponse().getContentAsString())
                .path("data")
                .path("defaultWorkspaceId")
                .asText();
        assertFalse(workspaceId.isBlank());

        MvcResult sessionResult = mockMvc.perform(post("/v1/chat/sessions")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Workspace-Id", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"persona":"agency","title":"Integration Test Session"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        String sessionId = objectMapper.readTree(sessionResult.getResponse().getContentAsString())
                .path("data")
                .path("sessionId")
                .asText();
        assertFalse(sessionId.isBlank());

        MvcResult msgResult = mockMvc.perform(post("/v1/chat/sessions/" + sessionId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Workspace-Id", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"What analyses did I run recently?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode data = objectMapper.readTree(msgResult.getResponse().getContentAsString()).path("data");
        assertEquals("assistant", data.path("role").asText());
        assertFalse(data.path("content").asText().isBlank());

        JsonNode traces = data.path("toolTraces");
        assertTrue(traces.isArray() && !traces.isEmpty());

        boolean hasListTool = false;
        for (JsonNode trace : traces) {
            if ("list_recent_analyses".equals(trace.path("tool").asText())) {
                hasListTool = true;
                assertTrue(trace.path("success").asBoolean());
            }
        }
        assertTrue(hasListTool, "Expected list_recent_analyses in toolTraces");

        boolean hasRecentCard = false;
        for (JsonNode card : data.path("cards")) {
            if ("recent_analyses".equals(card.path("type").asText())) {
                hasRecentCard = true;
            }
        }
        assertTrue(hasRecentCard, "Expected recent_analyses card");
    }
}
