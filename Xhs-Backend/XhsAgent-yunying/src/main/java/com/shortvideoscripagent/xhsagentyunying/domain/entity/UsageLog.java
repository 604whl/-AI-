package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("usage_log")
public class UsageLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String action;

    private String taskId;

    private OffsetDateTime createdAt;
}
