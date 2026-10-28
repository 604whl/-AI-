package com.shortvideoscripagent.xhsagentyunying.dto.hottopic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * S3-9 热点样本列表响应。
 * <p>
 * 同一热点（同平台 + 同归一化标题）的多条历史快照，供趋势对比使用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotTopicSamplesResponse {

    /** 热点 ID */
    private Long hotTopicId;

    /** 热点标题 */
    private String title;

    /** 平台 */
    private String platform;

    /** 归一化标题（去重键） */
    private String normalizedTitle;

    /** 历史样本（按采集时间升序） */
    private List<Sample> samples;

    /**
     * 单条样本。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Sample {
        private Long id;
        private String title;
        private String sourceUrl;
        private OffsetDateTime fetchedAt;
        private Integer heatScore;
        private String sourceName;
        private String sourceType;
        private String confidence;
        private Boolean isRealtime;
        /** active / superseded */
        private String status;
    }
}
