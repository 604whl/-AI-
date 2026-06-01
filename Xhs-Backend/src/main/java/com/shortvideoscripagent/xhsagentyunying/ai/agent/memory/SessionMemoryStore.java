package com.shortvideoscripagent.xhsagentyunying.ai.agent.memory;

import com.shortvideoscripagent.xhsagentyunying.domain.entity.ChatSession;

import java.util.Map;

public interface SessionMemoryStore {

    void touchSession(ChatSession session);

    void cacheContextSnippet(String sessionId, Map<String, Object> snippet);

    void clearSession(String sessionId);
}
