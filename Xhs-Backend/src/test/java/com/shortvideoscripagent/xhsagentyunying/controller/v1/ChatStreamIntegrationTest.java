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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.agent.enabled=true",
        "app.agent.mock-enabled=true",
        "app.ai.mock-enabled=true"
})
class ChatStreamIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void sendMessageStream_emitsDoneEvent() throws Exception {
        String email = "chat-stream-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                + "@example.com";
        // /v1/auth/register 按 IP 限流 3 次/小时。测试共享同一个 remoteAddr，
        // 重复运行会互相污染（表现为随机 429）。这里为每个用例分配唯一的伪造 IP，
        // 使用限流拦截器本身支持的 X-Forwarded-For 头，使用例之间完全隔离。
        String clientIp = "10.0.1."
                + (UUID.randomUUID().getLeastSignificantBits() & 0xFF);

        MvcResult authResult = mockMvc.perform(post("/v1/auth/register")
                        .header("X-Forwarded-For", clientIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Test1234","displayName":"Stream Tester"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(authResult.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();

        // 多租户隔离：WorkspaceInterceptor 对非豁免路径强制要求 X-Workspace-Id，
        // 缺失时返回 400 workspace_id_required。注册时后端已自动创建默认工作区。
        String workspaceId = objectMapper.readTree(authResult.getResponse().getContentAsString())
                .path("data")
                .path("defaultWorkspaceId")
                .asText();

        MvcResult sessionResult = mockMvc.perform(post("/v1/chat/sessions")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Workspace-Id", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"persona":"agency","title":"Stream Test"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String sessionId = objectMapper.readTree(sessionResult.getResponse().getContentAsString())
                .path("data")
                .path("sessionId")
                .asText();

        MvcResult asyncStarted = mockMvc.perform(post("/v1/chat/sessions/" + sessionId + "/messages/stream")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Workspace-Id", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {"content":"What analyses did I run recently?"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult streamResult = mockMvc.perform(asyncDispatch(asyncStarted))
                .andExpect(status().isOk())
                .andReturn();

        String body = streamResult.getResponse().getContentAsString();
        assertTrue(body.contains("event:done") || body.contains("event: done"),
                "Expected done SSE event in: " + body);

        if (body.contains("event:done")) {
            int idx = body.indexOf("event:done");
            String slice = body.substring(idx, Math.min(idx + 800, body.length()));
            int dataIdx = slice.indexOf("data:");
            assertTrue(dataIdx >= 0);
            String json = slice.substring(dataIdx + 5).trim();
            JsonNode done = objectMapper.readTree(json);
            assertFalse(done.path("content").asText().isBlank());
            assertTrue(done.path("toolTraces").isArray() && done.path("toolTraces").size() > 0);
        }
    }
}
