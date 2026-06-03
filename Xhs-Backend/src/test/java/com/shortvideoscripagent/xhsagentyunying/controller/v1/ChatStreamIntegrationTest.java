package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

        MvcResult authResult = mockMvc.perform(post("/v1/auth/register")
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

        MvcResult sessionResult = mockMvc.perform(post("/v1/chat/sessions")
                        .header("Authorization", "Bearer " + token)
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
