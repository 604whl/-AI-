package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("workspace")
public class Workspace {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String name;

    private String slug;

    private Long ownerUserId;

    private String industry;

    private String region;

    private String defaultLocale;

    private Integer defaultQuota;

    private String status;

    /**
     * S4-1：当前订阅状态（冗余自 app.subscription），便于列表查询与鉴权。
     */
    private String subscriptionStatus;

    /**
     * S4-1：当前套餐编码（冗余自 app.subscription），便于列表查询与鉴权。
     */
    private String planCode;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
