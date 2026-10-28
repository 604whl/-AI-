package com.shortvideoscripagent.xhsagentyunying.dto.hottopic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * S3-9 热点采集刷新请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotTopicRefreshRequest {

    /** 平台代码（不传则使用默认平台 xiaohongshu） */
    private String platform;

    /** 品类代码（不传表示不限品类） */
    private String category;
}
