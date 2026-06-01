package com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.impl;

import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.AgentTool;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolContext;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolResult;
import com.shortvideoscripagent.xhsagentyunying.ai.fixture.SampleAnalysisReport;
import com.shortvideoscripagent.xhsagentyunying.ai.rag.RagChunk;
import com.shortvideoscripagent.xhsagentyunying.ai.rag.RagContextBuilder;
import com.shortvideoscripagent.xhsagentyunying.ai.rag.RagQuery;
import com.shortvideoscripagent.xhsagentyunying.ai.rag.RagRetriever;
import com.shortvideoscripagent.xhsagentyunying.config.AppAiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SearchKbTool implements AgentTool {

    private final RagRetriever ragRetriever;
    private final RagContextBuilder ragContextBuilder;
    private final AppAiProperties appAiProperties;

    @Override
    public String name() {
        return "search_kb";
    }

    @Override
    public String description() {
        return "检索垂类爆文案例、标题模板、转化话术等知识库内容";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "检索关键词或内容摘要"),
                        "docTypes", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string")
                        ),
                        "contentType", Map.of("type", "string"),
                        "topK", Map.of("type", "integer", "default", 5)
                ),
                "required", List.of("query")
        );
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
        String query = stringArg(arguments, "query");
        if (query.isBlank()) {
            return ToolResult.fail("query_required");
        }

        List<String> docTypes = listArg(arguments, "docTypes");
        if (docTypes.isEmpty()) {
            docTypes = List.of("viral_case", "title_pattern", "structure_template", "cta_snippet");
        }
        String contentType = stringArg(arguments, "contentType");
        int topK = intArg(arguments, "topK", appAiProperties.getRag().getTopK());

        List<RagChunk> chunks = ragRetriever.retrieve(new RagQuery(
                query,
                docTypes,
                contentType.isBlank() ? null : contentType,
                context.persona(),
                topK
        ));

        if (chunks.isEmpty() && !appAiProperties.getRag().isEnabled()) {
            chunks = mockChunks();
        }

        List<Map<String, Object>> chunkMaps = new ArrayList<>();
        for (RagChunk chunk : chunks) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("docId", chunk.docId());
            item.put("docType", chunk.docType());
            item.put("content", chunk.content());
            item.put("score", chunk.score());
            item.put("metadata", chunk.metadata());
            chunkMaps.add(item);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chunks", chunkMaps);
        payload.put("formatted", ragContextBuilder.build(chunks));
        return ToolResult.ok(payload);
    }

    private List<RagChunk> mockChunks() {
        return List.of(
                new RagChunk(
                        "MOCK-01",
                        "viral_case",
                        "26届英国留学生｜秋招时间线一张图讲清\n要点: 结果前置; 六周时间线; CTA 私信领表",
                        0.91,
                        Map.of("contentType", "TIMELINE", "ctr", 90)
                ),
                new RagChunk(
                        "MOCK-02",
                        "title_pattern",
                        "双非逆袭字节｜6周上岸时间线全公开",
                        0.86,
                        Map.of("contentType", "OFFER", "ctr", 88)
                )
        );
    }

    static String stringArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    static int intArg(Map<String, Object> args, String key, int defaultValue) {
        Object value = args.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? defaultValue : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    @SuppressWarnings("unchecked")
    static List<String> listArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
