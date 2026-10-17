package com.shortvideoscripagent.xhsagentyunying.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

public class WorkspaceDtos {

    private WorkspaceDtos() {
    }

    @Data
    public static class CreateWorkspaceRequest {
        @NotBlank(message = "name is required")
        @Size(max = 60, message = "name too long")
        private String name;

        @Size(max = 80, message = "slug too long")
        @Pattern(regexp = "^[a-z0-9-]+$", message = "slug must be lowercase letters, digits or dashes")
        private String slug;

        @Size(max = 40)
        private String industry;

        @Size(max = 8)
        private String region;

        @Size(max = 16)
        private String defaultLocale;

        private Integer defaultQuota;
    }

    @Data
    public static class InviteMemberRequest {
        @NotBlank(message = "email is required")
        @Size(max = 120)
        private String email;

        /**
         * EDITOR / REVIEWER / VIEWER。OWNER/ADMIN 仅能由所有者修改。
         */
        @NotBlank(message = "role is required")
        @Pattern(regexp = "^(EDITOR|REVIEWER|VIEWER)$", message = "invalid role")
        private String role;
    }

    @Data
    public static class UpdateMemberRoleRequest {
        @NotBlank(message = "role is required")
        @Pattern(regexp = "^(EDITOR|REVIEWER|VIEWER)$", message = "invalid role")
        private String role;
    }

    @Data
    public static class UpdateWorkspaceRequest {
        @Size(max = 60, message = "name too long")
        private String name;

        @Size(max = 80, message = "slug too long")
        @Pattern(regexp = "^[a-z0-9-]+$", message = "slug must be lowercase letters, digits or dashes")
        private String slug;

        @Size(max = 40)
        private String industry;

        @Size(max = 8)
        private String region;

        @Size(max = 16)
        private String defaultLocale;

        /** null = 不修改；正整数 = 新每日配额 */
        private Integer defaultQuota;
    }
}