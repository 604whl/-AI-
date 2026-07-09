package com.shortvideoscripagent.xhsagentyunying.ai.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JsonReportParserTest {

    private JsonReportParser parser;

    @BeforeEach
    void setUp() {
        parser = new JsonReportParser(new ObjectMapper());
    }

    @Test
    void parseAnalysisReport_enrichesScoreLevel() {
        String raw = """
                {
                  "contentType": "TIMELINE",
                  "secondaryTags": [],
                  "structure": {
                    "hook": "test",
                    "emotionArc": ["起", "承", "转", "合"],
                    "savePoints": ["a"],
                    "conversionPath": "path",
                    "cta": { "text": "cta", "rating": 4, "comment": "ok" }
                  },
                  "scores": {
                    "ctr": { "score": 80, "reason": "good" },
                    "emotion": { "score": 70, "reason": "ok" },
                    "collect": { "score": 85, "reason": "ok" },
                    "conversion": { "score": 60, "reason": "ok" },
                    "viral": { "score": 75, "reason": "ok" }
                  },
                  "issues": [
                    { "severity": "low", "category": "ctr", "description": "d1", "suggestion": "s1" },
                    { "severity": "medium", "category": "emotion", "description": "d2", "suggestion": "s2" },
                    { "severity": "high", "category": "cta", "description": "d3", "suggestion": "s3" }
                  ],
                  "optimizations": {
                    "title": [{ "text": "t1", "reason": "r1" }],
                    "structure": [{ "text": "s1", "reason": "r2" }],
                    "emotion": [{ "text": "e1", "reason": "r3" }],
                    "cta": []
                  },
                  "complianceWarnings": []
                }
                """;

        Map<String, Object> report = parser.parseAnalysisReport(raw);
        @SuppressWarnings("unchecked")
        Map<String, Object> ctr = (Map<String, Object>) ((Map<?, ?>) report.get("scores")).get("ctr");
        assertEquals("high", ctr.get("level"));
        assertNotNull(report.get("contentType"));
    }

    @Test
    void parseBodyGenerate_acceptsObjectOutline() {
        String raw = """
                {
                  "body": "围绕原文生成的正文",
                  "structureOutline": [
                    {"section": "hook", "summary": "开头"},
                    {"section": "problem_amplification", "summary": "放大"},
                    {"section": "real_experience", "summary": "经历"},
                    {"section": "result_showcase", "summary": "结果"},
                    {"section": "cta", "summary": "行动"}
                  ],
                  "cta": "评论领取资料",
                  "complianceWarnings": []
                }
                """;

        JsonReportParser.ParsedBodyGenerate parsed = parser.parseBodyGenerate(raw);

        assertEquals("围绕原文生成的正文", parsed.body());
        assertEquals(5, parsed.structureOutline().size());
        assertEquals("hook", parsed.structureOutline().get(0).getSection());
        assertEquals("评论领取资料", parsed.cta());
    }

    @Test
    void parseBodyGenerate_acceptsStringOutlineAndUsesLastAsCta() {
        String raw = """
                {
                  "body": "围绕原文生成的正文",
                  "structureOutline": ["开头", "放大", "经历", "结果", "评论领取资料"],
                  "complianceWarnings": []
                }
                """;

        JsonReportParser.ParsedBodyGenerate parsed = parser.parseBodyGenerate(raw);

        assertEquals(5, parsed.structureOutline().size());
        assertEquals("problem_amplification", parsed.structureOutline().get(1).getSection());
        assertEquals("评论领取资料", parsed.cta());
    }
}
