package com.shortvideoscripagent.xhsagentyunying.dto.kb;

import lombok.Data;

/**
 * 关键词检索命中项（doc 维度 + 命中片段）。
 */
@Data
public class KbDocumentSearchHit {

    private String docId;
    private String title;
    private String docType;
    private String contentType;
    /** 命中片段（截断后的上下文）。 */
    private String snippet;
    /** 命中的字段：title / content。 */
    private String matchedField;
}
