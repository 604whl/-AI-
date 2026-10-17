package com.shortvideoscripagent.xhsagentyunying.freeze;

import java.util.Optional;

/**
 * 阶段 0：内容状态机。
 * <p>
 * 状态转换图：
 * <pre>
 *                ┌─────────┐
 *                │  DRAFT  │
 *                └────┬────┘
 *                     │ submit
 *                     ▼
 *              ┌─────────────┐
 *       ┌──────│  IN_REVIEW  │──────┐
 *       │      └─────────────┘      │
 *   approve│                     reject│
 *       │      │      │      │      │
 *       │    request_changes
 *       ▼      ▼      ▼      ▼      ▼
 *   ┌────────┐ ┌──────────────┐ ┌─────────────┐
 *   │APPROVED│ │   REJECTED   │ │ (back to    │
 *   └───┬────┘ └──────────────┘ │  DRAFT or   │
 *       │ schedule              │  ARCHIVED)  │
 *       ▼                       └─────────────┘
 *   ┌──────────┐
 *   │SCHEDULED │
 *   └──┬───────┘
 *      │ publish（success）
 *      ▼
 *  ┌──────────┐  archive   ┌──────────┐
 *  │PUBLISHED │──────────▶│ARCHIVED  │
 *  └──────────┘            └──────────┘
 * </pre>
 * <p>
 * 禁止的非法转换由 {@link #canTransition(ContentStatus, ContentStatus)} 强制拒绝，
 * 例如 {@code DRAFT → PUBLISHED}（绕过审核）、{@code PUBLISHED → DRAFT}（已发不可改）。
 */
public enum ContentStatus {
    DRAFT,
    IN_REVIEW,
    APPROVED,
    SCHEDULED,
    PUBLISHED,
    ARCHIVED,
    REJECTED;

    /**
     * 状态机：判断 from → to 是否合法。
     * <p>
     * 状态机本身不知道用户角色和上下文；业务调用方（service / controller）在调用本方法前自行校验
     * "操作者角色、是否拥有该草稿、是否完成审核要求"等。
     */
    public static boolean canTransition(ContentStatus from, ContentStatus to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return true;
        }
        return switch (from) {
            case DRAFT -> to == IN_REVIEW || to == ARCHIVED;
            case IN_REVIEW -> to == APPROVED || to == REJECTED || to == DRAFT;
            case APPROVED -> to == SCHEDULED || to == DRAFT || to == ARCHIVED;
            case SCHEDULED -> to == PUBLISHED || to == APPROVED || to == ARCHIVED;
            case PUBLISHED -> to == ARCHIVED;
            case REJECTED -> to == DRAFT || to == ARCHIVED;
            case ARCHIVED -> false; // 归档只读
        };
    }

    /**
     * 判断状态是否"已发布/已归档"——即不可再被修改的状态。
     */
    public boolean isTerminal() {
        return this == PUBLISHED || this == ARCHIVED;
    }

    /**
     * 是否处于审核流程中（处于此状态时其他角色不能直接编辑内容）。
     */
    public boolean isUnderReview() {
        return this == IN_REVIEW;
    }

    public static Optional<ContentStatus> tryParse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ContentStatus.valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
