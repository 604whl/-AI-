package com.shortvideoscripagent.xhsagentyunying.ai.rag;

import java.util.Map;

public record RagChunk(
        String docId,
        String docType,
        String content,
        double score,
        Map<String, Object> metadata
) {
}
