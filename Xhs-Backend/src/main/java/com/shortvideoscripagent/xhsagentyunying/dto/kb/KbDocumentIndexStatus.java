package com.shortvideoscripagent.xhsagentyunying.dto.kb;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 逐文档索引状态。
 */
@Data
public class KbDocumentIndexStatus {

    private String docId;
    /** 源文件是否仍然存在 */
    private boolean sourceFilePresent;
    /** 源文件路径（不存在时为 null） */
    private String sourceFilePath;
    /** 最近一次索引时间（该 doc 所有 chunk 的最新 updated_at） */
    private OffsetDateTime lastIndexedAt;
    private long chunkCount;
    private long embeddedChunkCount;
    /** 源文件比索引更新（需要重新入库） */
    private boolean stale;
}