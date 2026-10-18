package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 热搜/热门话题池，Scheduler 每 4h 从 GetHotTopicsTool 拉取写入。
 */
@Data
@TableName("hot_topic")
public class HotTopic {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String platform;

    private String category;

    private String title;

    private Integer heatScore;

    private OffsetDateTime fetchedAt;

    private Map<String, Object> rawPayload;

    private Boolean promotedToPool;

    public static final String PLATFORM_XHS = "xiaohongshu";
}