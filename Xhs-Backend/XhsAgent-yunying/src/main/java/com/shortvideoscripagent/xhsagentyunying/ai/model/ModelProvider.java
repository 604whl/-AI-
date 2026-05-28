package com.shortvideoscripagent.xhsagentyunying.ai.model;

/**
 * 多模型统一抽象：主路径 DashScope，可扩展 OpenAI 兼容 API。
 */
public interface ModelProvider {

    String id();

    String chat(String systemPrompt, String userPrompt);

    boolean supportsVision();
}
