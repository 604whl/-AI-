package com.shortvideoscripagent.xhsagentyunying.dto.kb;

public record KbChunkPreviewResponse(
        String docId,
        String chunkType,
        String content,
        int charCount
) {
}
