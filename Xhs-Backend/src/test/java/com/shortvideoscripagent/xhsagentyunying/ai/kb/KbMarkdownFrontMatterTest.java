package com.shortvideoscripagent.xhsagentyunying.ai.kb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KbMarkdownFrontMatterTest {

    @Test
    void parse_extractsFieldsAndBody() {
        String raw = """
                ---
                doc_id: CASE-01
                doc_type: viral_case
                tags: 逆袭,职场
                ---
                正文第一段。
                """;
        KbMarkdownFrontMatter.Parsed parsed = KbMarkdownFrontMatter.parse(raw);
        assertEquals("CASE-01", parsed.fields().get("doc_id"));
        assertEquals("viral_case", parsed.fields().get("doc_type"));
        assertTrue(parsed.body().contains("正文第一段"));
        assertEquals(2, KbMarkdownFrontMatter.splitList("逆袭,职场").size());
    }
}
