package com.shortvideoscripagent.xhsagentyunying.ai.model;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

@Component
@ConditionalOnProperty(name = "app.ai.mock-enabled", havingValue = "false")
public class DashScopeModelProvider implements ModelProvider {

    private final ChatClient chatClient;
    private final ChatClient visionChatClient;

    public DashScopeModelProvider(
            ChatClient chatClient,
            @Autowired(required = false) @Qualifier("visionChatClient") ChatClient visionChatClient
    ) {
        this.chatClient = chatClient;
        this.visionChatClient = visionChatClient;
    }

    @Override
    public String id() {
        return "dashscope";
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
    }

    @Override
    public String chatWithImage(String systemPrompt, String userPrompt, byte[] imageBytes, String mimeType) {
        if (imageBytes == null || imageBytes.length == 0) {
            return "";
        }
        ChatClient client = visionChatClient != null ? visionChatClient : chatClient;
        MimeType resolved = resolveMimeType(mimeType);
        Media media = Media.builder()
                .mimeType(resolved)
                .data(new ByteArrayResource(imageBytes))
                .build();
        return client.prompt()
                .system(systemPrompt)
                .user(u -> u.text(userPrompt).media(media))
                .call()
                .content();
    }

    @Override
    public boolean supportsVision() {
        return true;
    }

    private static MimeType resolveMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return MimeTypeUtils.IMAGE_JPEG;
        }
        return switch (mimeType.toLowerCase()) {
            case "image/png" -> MimeTypeUtils.IMAGE_PNG;
            case "image/webp" -> MimeType.valueOf("image/webp");
            default -> MimeTypeUtils.IMAGE_JPEG;
        };
    }
}
