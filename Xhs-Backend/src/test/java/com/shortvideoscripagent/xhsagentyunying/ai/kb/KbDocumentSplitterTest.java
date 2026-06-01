package com.shortvideoscripagent.xhsagentyunying.ai.kb;

import com.shortvideoscripagent.xhsagentyunying.config.AppKbProperties;
import com.shortvideoscripagent.xhsagentyunying.config.KbChunkingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KbDocumentSplitterTest {

    @Test
    void split_longChineseText_producesMultipleParts() {
        AppKbProperties props = new AppKbProperties();
        props.getChunking().setChunkSizeTokens(80);
        props.getChunking().setMinChunkSizeChars(40);

        TokenTextSplitter splitter = new KbChunkingConfig().kbTokenTextSplitter(props);
        KbDocumentSplitter documentSplitter = new KbDocumentSplitter(splitter);

        String body = "这是一段用于测试 Spring AI TokenTextSplitter 的中文正文。"
                + "我们希望在句号处尽量断开，并且当文本超过 token 阈值时自动切成多片。"
                + "第二段继续补充内容，模拟真实小红书爆款案例的正文长度与结构。"
                + "第三段加入行动号召：私信领取资料，评论区扣1获取模板。";

        KbSourceDescriptor source = new KbSourceDescriptor(
                "CASE-TEST",
                "viral_case",
                "OFFER",
                "测试标题",
                List.of("测试"),
                List.of("agency"),
                body,
                null,
                Map.of("sourceFile", "case-test.md")
        );

        List<KbChunkDraft> chunks = documentSplitter.split(source);
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.size() >= 2, "expected multiple chunks, got " + chunks.size());
        assertTrue(chunks.get(0).chunkType().startsWith("part_"));
    }
}
