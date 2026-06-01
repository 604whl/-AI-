package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("chat_session")
public class ChatSession {

    @TableId
    private String id;

    private Long userId;

    private String title;

    private String persona;

    private String linkedTaskId;

    private String status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
