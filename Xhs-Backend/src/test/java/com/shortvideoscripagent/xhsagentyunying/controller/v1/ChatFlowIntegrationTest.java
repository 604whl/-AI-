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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
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

        MvcResult authResult = mockMvc.perform(post("/v1/auth/register")
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

        MvcResult sessionResult = mockMvc.perform(post("/v1/chat/sessions")
                        .header("Authorization", "Bearer " + token)
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
