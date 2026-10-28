package com.shortvideoscripagent.xhsagentyunying.dto.kb;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 单个 chunk 的摘要（不返回 embedding，也不返回完整正文长度以外的敏感内容）。
 */
@Data
public class KbDocumentChunkSummary {

    private String chunkType;
    /** chunk 正文字符数。 */
    private int contentLength;
    private boolean hasEmbedding;
    private OffsetDateTime updatedAt;
}
