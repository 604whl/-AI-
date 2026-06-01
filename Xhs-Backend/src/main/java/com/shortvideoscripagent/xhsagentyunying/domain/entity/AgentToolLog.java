package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("agent_tool_log")
public class AgentToolLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    private Long userId;

    private String toolName;

    private String inputJson;

    private String outputSummary;

    private Boolean success;

    private Integer latencyMs;

    private OffsetDateTime createdAt;
}
