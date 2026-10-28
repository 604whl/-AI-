package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.PaginatedResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.competitor.CompetitorAccountCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.competitor.CompetitorAccountCreateResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.competitor.CompetitorAccountListItem;
import com.shortvideoscripagent.xhsagentyunying.dto.competitor.CompetitorNoteCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.competitor.CompetitorNoteCreateResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.competitor.CompetitorNoteListItem;
import com.shortvideoscripagent.xhsagentyunying.service.CompetitorService;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceGuards;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceRole;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * S3-10 竞品库 API。
 * <p>
 * 实际路径：{@code server.servlet.context-path=/api} + {@code /v1/competitors}
 * → {@code /api/v1/competitors}。
 * </p>
 */
@RestController
@RequestMapping("/v1/competitors")
@RequiredArgsConstructor
@Tag(name = "Competitors", description = "竞品账号与竞品笔记库（阶段 3 S3-10）")
public class CompetitorController {

    private static final int CODE_UNAUTHORIZED = 40101;
    private static final int CODE_FORBIDDEN = 40301;
    /** 404 竞品账号 / 笔记不存在或越权 */
    private static final int CODE_COMPETITOR_NOT_FOUND = 40474;

    private final CompetitorService competitorService;
    private final WorkspaceService workspaceService;

    // ── 竞品账号 ────────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "创建竞品账号",
            description = "ANALYST/EDITOR/ADMIN/OWNER；同 workspace + 平台 + 账号名唯一；"
                    + "错误码 400 COMPETITOR_INVALID / 409 COMPETITOR_DUPLICATE")
    public ApiResponse<CompetitorAccountCreateResponse> createAccount(
            @Valid @RequestBody CompetitorAccountCreateRequest request) {
        Long userId = requireUserId();
        String workspaceId = requireWorkspace();
        requireAnalyst(workspaceId, userId);
        return ApiResponse.ok(competitorService.createAccount(workspaceId, userId, request));
    }

    @GetMapping
    @Operation(summary = "列出竞品账号", description = "全部角色可读；含笔记数聚合")
    public ApiResponse<PaginatedResponse<CompetitorAccountListItem>> listAccounts(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireUserId();
        String workspaceId = requireWorkspace();
        return ApiResponse.ok(competitorService.listAccounts(workspaceId, platform, category, status, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询竞品账号详情", description = "全部角色可读；错误码 404 COMPETITOR_ACCOUNT_NOT_FOUND")
    public ApiResponse<CompetitorAccountListItem> getAccount(@PathVariable String id) {
        requireUserId();
        String workspaceId = requireWorkspace();
        return ApiResponse.ok(competitorService.getAccount(workspaceId, parseUuid(id)));
    }

    // ── 竞品笔记 ────────────────────────────────────────────────────────

    @PostMapping("/{id}/notes")
    @Operation(summary = "新增竞品笔记",
            description = "ANALYST/EDITOR/ADMIN/OWNER；<b>evidenceText 必填</b>，"
                    + "没有真实原文片段不得入库（400 EVIDENCE_TEXT_REQUIRED）；"
                    + "错误码 404 COMPETITOR_ACCOUNT_NOT_FOUND")
    public ApiResponse<CompetitorNoteCreateResponse> createNote(
            @PathVariable String id,
            @Valid @RequestBody CompetitorNoteCreateRequest request) {
        Long userId = requireUserId();
        String workspaceId = requireWorkspace();
        requireAnalyst(workspaceId, userId);
        return ApiResponse.ok(competitorService.createNote(workspaceId, userId, parseUuid(id), request));
    }

    @GetMapping("/{id}/notes")
    @Operation(summary = "列出竞品笔记", description = "全部角色可读；按采集时间倒序")
    public ApiResponse<PaginatedResponse<CompetitorNoteListItem>> listNotes(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireUserId();
        String workspaceId = requireWorkspace();
        return ApiResponse.ok(competitorService.listNotes(workspaceId, parseUuid(id), page, size));
    }

    @GetMapping("/{id}/notes/{noteId}")
    @Operation(summary = "查询竞品笔记详情",
            description = "全部角色可读；错误码 404 COMPETITOR_NOTE_NOT_FOUND")
    public ApiResponse<CompetitorNoteListItem> getNote(@PathVariable String id, @PathVariable String noteId) {
        requireUserId();
        String workspaceId = requireWorkspace();
        return ApiResponse.ok(competitorService.getNote(workspaceId, parseUuid(id), parseUuid(noteId)));
    }

    // ── 内部工具 ────────────────────────────────────────────────────────

    private Long requireUserId() {
        Long userId = RequestContext.getUserIdOrNull();
        if (userId == null) {
            throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        }
        return userId;
    }

    private String requireWorkspace() {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        WorkspaceGuards.requireMember(workspaceId);
        return workspaceId;
    }

    /**
     * ANALYST / EDITOR / ADMIN / OWNER 可写。
     */
    private void requireAnalyst(String workspaceId, Long userId) {
        WorkspaceRole role = workspaceService.findRole(userId, workspaceId);
        if (role == null || !role.atLeast(WorkspaceRole.EDITOR)) {
            throw new BusinessException(CODE_FORBIDDEN, "forbidden");
        }
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(CODE_COMPETITOR_NOT_FOUND, "COMPETITOR_NOT_FOUND");
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(CODE_COMPETITOR_NOT_FOUND, "COMPETITOR_NOT_FOUND");
        }
    }
}
