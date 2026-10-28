package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shortvideoscripagent.xhsagentyunying.config.mybatis.JsonbStringTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 热点采集缓存。
 * <p>
 * 同窗口命中缓存时不重复调用外部源：{@code fetched_at + ttl_seconds > now()} 即视为有效。
 * </p>
 */
@Data
@TableName(value = "hot_topic_cache", autoResultMap = true)
public class HotTopicCache {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 平台代码 */
    private String platform;

    /** 品类代码，{@code ""} 表示不限品类 */
    private String category;

    /** 缓存载荷 JSON：{"degraded":bool,"degradedReason":"...","items":[...]} */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String payload;

    /** 本次采集/写入时间 */
    private OffsetDateTime fetchedAt;

    /** 缓存有效期（秒） */
    private Integer ttlSeconds;

    /** 不限品类的 category 哨兵值 */
    public static final String CATEGORY_ALL = "";
}
