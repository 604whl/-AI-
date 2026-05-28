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
                  "issues": [{
                    "severity": "low",
                    "category": "ctr",
                    "description": "d",
                    "suggestion": "s"
                  }],
                  "optimizations": {
                    "title": [],
                    "structure": [],
                    "emotion": [],
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
}
