package com.shortvideoscripagent.xhsagentyunying.dto.kb;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 知识库文档详情（文档元信息 + chunk 列表）。
 */
@Data
public class KbDocumentDetail {

    private String docId;
    private String title;
    private String docType;
    private String contentType;
    private List<String> persona;
    private List<String> tags;
    private long chunkCount;
    private boolean hasEmbedding;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String workspaceId;
    private List<KbDocumentChunkSummary> chunks;
}
