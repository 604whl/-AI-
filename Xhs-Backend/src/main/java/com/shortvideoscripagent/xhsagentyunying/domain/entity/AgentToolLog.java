package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.shortvideoscripagent.xhsagentyunying.config.mybatis.JsonbStringTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName(value = "agent_tool_log", autoResultMap = true)
public class AgentToolLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    private Long userId;

    private String toolName;

    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String inputJson;

    private String outputSummary;

    private Boolean success;

    private Integer latencyMs;

    private OffsetDateTime createdAt;
}
