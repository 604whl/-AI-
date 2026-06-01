package com.shortvideoscripagent.xhsagentyunying.config;

import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KbChunkingConfig {

    @Bean
    public TokenTextSplitter kbTokenTextSplitter(AppKbProperties kbProperties) {
        AppKbProperties.Chunking c = kbProperties.getChunking();
        return TokenTextSplitter.builder()
                .withChunkSize(c.getChunkSizeTokens())
                .withMinChunkSizeChars(c.getMinChunkSizeChars())
                .withMinChunkLengthToEmbed(c.getMinChunkLengthToEmbed())
                .withMaxNumChunks(c.getMaxNumChunks())
                .withKeepSeparator(c.isKeepSeparator())
                .build();
    }
}
