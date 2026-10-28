package com.shortvideoscripagent.xhsagentyunying.dto.hottopic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * S3-9 热点手动导入请求（表单 / JSON 形态）。
 * <p>
 * 手动导入的热点一律：{@code source_type='MANUAL_IMPORT'} + {@code confidence='LOW'}
 * + {@code is_realtime=FALSE}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotTopicImportRequest {

    /** 待导入条目 */
    private List<Item> items;

    /**
     * 单条热点。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        /** 热点标题（必填） */
        private String title;
        /** 平台代码（默认 xiaohongshu） */
        private String platform;
        /** 品类代码 */
        private String category;
        /** 热度分（默认 0） */
        private Integer heatScore;
        /** 来源名称（默认 manual_import） */
        private String sourceName;
        /** 来源链接 */
        private String sourceUrl;
    }
}
