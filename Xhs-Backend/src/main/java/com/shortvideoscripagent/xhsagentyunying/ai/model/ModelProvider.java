package com.shortvideoscripagent.xhsagentyunying.ai.model;

/**
 * 多模型统一抽象：默认 DeepSeek 文本聊天，可按配置启用其他 OpenAI 兼容提供商。
 */
public interface ModelProvider {

    String id();

    default String modelName() {
        return id();
    }

    String chat(String systemPrompt, String userPrompt);

    default boolean supportsVision() {
        return false;
    }
}
