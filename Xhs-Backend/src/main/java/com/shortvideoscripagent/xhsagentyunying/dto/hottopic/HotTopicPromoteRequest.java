package com.shortvideoscripagent.xhsagentyunying.dto.hottopic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * S3-9 热点 → 选题池提升请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotTopicPromoteRequest {

    /** 选题品类（覆盖热点自身品类） */
    private String category;

    /** 选题标签（追加到热点来源标签之后） */
    private List<String> tags;
}
