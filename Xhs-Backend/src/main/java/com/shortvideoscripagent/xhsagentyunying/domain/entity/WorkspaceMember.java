package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("workspace_member")
public class WorkspaceMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String workspaceId;

    private Long userId;

    /** OWNER > ADMIN > EDITOR > REVIEWER > VIEWER */
    private String role;

    private OffsetDateTime joinedAt;

    private OffsetDateTime lastActiveAt;

    private String status;
}