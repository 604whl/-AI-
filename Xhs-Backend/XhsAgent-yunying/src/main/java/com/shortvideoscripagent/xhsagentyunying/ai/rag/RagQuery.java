package com.shortvideoscripagent.xhsagentyunying.ai.rag;

import java.util.List;

public record RagQuery(
        String queryText,
        List<String> docTypes,
        String contentType,
        String persona,
        int topK
) {
}
