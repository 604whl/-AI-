package com.shortvideoscripagent.xhsagentyunying.dto.kb;

import lombok.Data;

/**
 * 删除知识库文档的结果。
 */
@Data
public class KbDocumentDeleteResult {

    private String docId;
    /** 被删除的 chunk 行数。 */
    private int deletedChunks;
    /** 源文件是否已删除。 */
    private boolean sourceFileRemoved;
    /** 源文件清理说明（未删除时说明原因）。 */
    private String sourceFileCleanupNote;
}
