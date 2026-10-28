package com.shortvideoscripagent.xhsagentyunying.dto.hottopic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * S3-9 热点 → 选题池提升响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotTopicPromoteResponse {

    /** 新建的选题池 ID */
    private Long topicPoolId;

    /** 热点 ID */
    private Long hotTopicId;

    /** 是否已存在同名选题（本次未新建） */
    private Boolean duplicate;

    /** 透传到选题池的来源名称 */
    private String sourceName;

    /** 透传到选题池的可信度 */
    private String confidence;

    /** 透传到选题池的来源采集时间 */
    private java.time.OffsetDateTime sourceFetchedAt;
}
