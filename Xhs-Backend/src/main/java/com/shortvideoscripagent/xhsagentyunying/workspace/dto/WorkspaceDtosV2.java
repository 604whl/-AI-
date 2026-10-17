package com.shortvideoscripagent.xhsagentyunying.workspace.dto;

import com.shortvideoscripagent.xhsagentyunying.domain.entity.Workspace;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.WorkspaceMember;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

public class WorkspaceDtosV2 {

    private WorkspaceDtosV2() {
    }

    @Data
    @Builder
    public static class WorkspaceDetailVo {
        private String id;
        private String name;
        private String slug;
        private String industry;
        private String region;
        private String defaultLocale;
        private Integer defaultQuota;
        private Long ownerUserId;
        private String role;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;

        public static WorkspaceDetailVo from(Workspace workspace, String role) {
            if (workspace == null) {
                return null;
            }
            return WorkspaceDetailVo.builder()
                    .id(workspace.getId())
                    .name(workspace.getName())
                    .slug(workspace.getSlug())
                    .industry(workspace.getIndustry())
                    .region(workspace.getRegion())
                    .defaultLocale(workspace.getDefaultLocale())
                    .defaultQuota(workspace.getDefaultQuota())
                    .ownerUserId(workspace.getOwnerUserId())
                    .role(role)
                    .createdAt(workspace.getCreatedAt())
                    .updatedAt(workspace.getUpdatedAt())
                    .build();
        }
    }

    @Data
    @Builder
    public static class MemberVo {
        private Long userId;
        private String email;
        private String displayName;
        private String role;
        private String status;
        private OffsetDateTime joinedAt;
        private OffsetDateTime lastActiveAt;

        public static MemberVo from(WorkspaceMember member, User user) {
            return MemberVo.builder()
                    .userId(member.getUserId())
                    .email(user == null ? null : user.getEmail())
                    .displayName(user == null ? null : user.getDisplayName())
                    .role(member.getRole())
                    .status(member.getStatus())
                    .joinedAt(member.getJoinedAt())
                    .lastActiveAt(member.getLastActiveAt())
                    .build();
        }
    }
}