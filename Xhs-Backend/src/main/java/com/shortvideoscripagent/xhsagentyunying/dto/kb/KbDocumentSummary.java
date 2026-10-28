package com.shortvideoscripagent.xhsagentyunying.dto.kb;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 知识库文档列表项（按 doc_id 聚合后的结果）。
 */
@Data
public class KbDocumentSummary {

    private String docId;
    private String title;
    private String docType;
    private String contentType;
    private List<String> persona;
    private List<String> tags;
    /** 该文档的 chunk 数量。 */
    private long chunkCount;
    /** 是否至少有一个 chunk 带 embedding。 */
    private boolean hasEmbedding;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String workspaceId;
}
