package com.shortvideoscripagent.xhsagentyunying.ai.kb;

import java.util.List;
import java.util.Map;

/**
 * 切片后、写入数据库前的中间结构。
 */
public record KbChunkDraft(
        String docId,
        String docType,
        String contentType,
        String title,
        List<String> tags,
        List<String> persona,
        String chunkType,
        String content,
        Map<String, Object> metadata
) {
}
