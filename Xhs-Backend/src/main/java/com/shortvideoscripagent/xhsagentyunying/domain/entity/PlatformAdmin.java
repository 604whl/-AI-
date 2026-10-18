package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName("platform_admin")
public class PlatformAdmin {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String role;
    private Boolean enabled;
    private OffsetDateTime createdAt;
    private OffsetDateTime revokedAt;
    private String notes;

    public boolean isActive() {
        return Boolean.TRUE.equals(enabled) && revokedAt == null;
    }
}
