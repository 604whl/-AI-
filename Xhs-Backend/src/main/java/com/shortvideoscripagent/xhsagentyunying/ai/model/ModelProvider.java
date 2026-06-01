package com.shortvideoscripagent.xhsagentyunying.ai.model;

/**
 * 多模型统一抽象：主路径 DashScope，可扩展 OpenAI 兼容 API。
 */
public interface ModelProvider {

    String id();

    String chat(String systemPrompt, String userPrompt);

    /**
     * 多模态对话；不支持视觉的提供商应返回空字符串并由调用方降级。
     */
    default String chatWithImage(String systemPrompt, String userPrompt, byte[] imageBytes, String mimeType) {
        return "";
    }

    boolean supportsVision();
}
