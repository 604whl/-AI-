package com.shortvideoscripagent.xhsagentyunying.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkspaceVo {
    private String id;
    private String name;
    private String slug;
    private String role;
}