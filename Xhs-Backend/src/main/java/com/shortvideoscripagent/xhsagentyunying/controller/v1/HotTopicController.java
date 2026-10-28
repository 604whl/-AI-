package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.CsvImportLog;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.CsvImportLogMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicImportRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicImportResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicListItem;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicListResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicPromoteRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicPromoteResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicRefreshRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicRefreshResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicSamplesResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.hottopic.HotTopicTrendResponse;
import com.shortvideoscripagent.xhsagentyunying.service.HotTopicService;
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
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * S3-9 热点数据产品化 API。
 * <p>
 * 实际路径：{@code server.servlet.context-path=/api} + {@code /v1/hot-topics}
 * → {@code /api/v1/hot-topics}。
 * </p>
 */
@RestController
@RequestMapping("/v1/hot-topics")
@RequiredArgsConstructor
@Tag(name = "Hot Topics", description = "热点数据产品化（阶段 3 S3-9）")
public class HotTopicController {

    private static final int CODE_UNAUTHORIZED = 40101;
    private static final int CODE_FORBIDDEN = 40301;
    private static final int CODE_CSV_SCHEMA_INVALID = 40062;

    /** CSV 错误明细最大保留条数 */
    private static final int MAX_ERRORS = 20;

    private final HotTopicService hotTopicService;
    private final CsvImportLogMapper csvImportLogMapper;
    private final WorkspaceService workspaceService;

    // ── 列表 ────────────────────────────────────────────────────────────

    /**
     * 列出热点（全部角色可读）。
     * <p>
     * 响应含顶层 {@code degraded} / {@code degradedReason} / {@code fallbackAvailable}；
     * 每条含 {@code sourceType} / {@code isRealtime}（前端据此标注"非实时"）。
     * </p>
     */
    @GetMapping
    @Operation(summary = "列出热点", description = "全部角色可读；含来源 / 可信度 / 是否实时 / 降级状态")
    public ApiResponse<HotTopicListResponse> list(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) Boolean promoted,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        requireUserId();
        requireWorkspace();
        return ApiResponse.ok(hotTopicService.list(platform, category, sourceType, promoted, page, size));
    }

    /**
     * 列出热点（旧路径别名，与 {@code GET /v1/hot-topics} 等价）。
     */
    @GetMapping("/list")
    @Operation(summary = "列出热点（旧路径别名）", description = "与 GET /v1/hot-topics 等价")
    public ApiResponse<HotTopicListResponse> listLegacy(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) Boolean promoted,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        requireUserId();
        requireWorkspace();
        return ApiResponse.ok(hotTopicService.list(platform, category, sourceType, promoted, page, size));
    }

    // ── 趋势 ────────────────────────────────────────────────────────────

    @GetMapping("/trend")
    @Operation(summary = "热点热度趋势",
            description = "全部角色可读；趋势输入为同平台 + 同归一化标题的多条快照；单条记录不得输出\"上升\"")
    public ApiResponse<HotTopicTrendResponse> trend(
            @RequestParam String title,
            @RequestParam(defaultValue = "7") int windowDays) {
        requireUserId();
        requireWorkspace();
        if (title == null || title.isBlank()) {
            throw new BusinessException(40010, "title is required");
        }
        int w = Math.min(Math.max(windowDays, 1), 30);
        return ApiResponse.ok(hotTopicService.computeTrend(title, w));
    }

    // ── 采集 ────────────────────────────────────────────────────────────

    @PostMapping("/refresh")
    @Operation(summary = "触发热点采集",
            description = "ADMIN/OWNER；命中缓存不重复调用外部源；未授权平台 403 CONNECTOR_NOT_AUTHORIZED；"
                    + "采集失败返回 degraded=true + degradedReason + fallbackAvailable=MANUAL_IMPORT")
    public ApiResponse<HotTopicRefreshResponse> refresh(
            @RequestBody(required = false) HotTopicRefreshRequest request) {
        Long userId = requireUserId();
        String workspaceId = requireWorkspace();
        requireAdmin(workspaceId, userId);
        return ApiResponse.ok(hotTopicService.refresh(workspaceId, userId, request));
    }

    // ── 手动导入 ────────────────────────────────────────────────────────

    @PostMapping("/import")
    @Operation(summary = "手动导入热点",
            description = "ANALYST+；multipart csv 或 JSON items；"
                    + "导入项一律 source_type=MANUAL_IMPORT + confidence=LOW + isRealtime=FALSE；"
                    + "错误码 400 CSV_SCHEMA_INVALID")
    public ApiResponse<HotTopicImportResponse> importTopics(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestBody(required = false) HotTopicImportRequest request) {
        Long userId = requireUserId();
        String workspaceId = requireWorkspace();
        requireAnalyst(workspaceId, userId);

        if (file != null && !file.isEmpty()) {
            String csvText;
            try {
                csvText = new String(file.getBytes(), StandardCharsets.UTF_8);
            } catch (Exception ex) {
                throw new BusinessException(CODE_CSV_SCHEMA_INVALID, "CSV_SCHEMA_INVALID: cannot_read_csv");
            }
            List<String> errors = new ArrayList<>();
            HotTopicImportResponse outcome;
            Long importLogId;
            try {
                outcome = hotTopicService.importCsv(workspaceId, userId,
                        file.getOriginalFilename(), csvText, errors);
                importLogId = writeImportLog(userId, workspaceId, file.getOriginalFilename(),
                        outcome.getImported(), outcome.getSkipped(), outcome.getErrorRows(), errors);
            } catch (BusinessException be) {
                writeImportLog(userId, workspaceId, file.getOriginalFilename(), 0, 0, 1, errors);
                throw be;
            }
            outcome.setImportLogId(importLogId);
            return ApiResponse.ok(outcome);
        }

        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException(CODE_CSV_SCHEMA_INVALID,
                    "CSV_SCHEMA_INVALID: csv_file_or_items_required");
        }
        return ApiResponse.ok(hotTopicService.importItems(workspaceId, userId, request));
    }

    // ── 样本 ────────────────────────────────────────────────────────────

    @GetMapping("/{id}/samples")
    @Operation(summary = "查询热点的历史样本",
            description = "全部角色可读；同平台 + 同归一化标题的多条快照（去重保留历史）；错误码 404 HOT_TOPIC_NOT_FOUND")
    public ApiResponse<HotTopicSamplesResponse> samples(@PathVariable Long id) {
        requireUserId();
        requireWorkspace();
        return ApiResponse.ok(hotTopicService.samples(id));
    }

    // ── 提升到选题池 ────────────────────────────────────────────────────

    @PostMapping("/{id}/promote")
    @Operation(summary = "热点提升到选题池",
            description = "EDITOR+；透传 source_name / confidence / source_fetched_at；错误码 404 HOT_TOPIC_NOT_FOUND")
    public ApiResponse<HotTopicPromoteResponse> promote(
            @PathVariable Long id,
            @RequestBody(required = false) @Valid HotTopicPromoteRequest request) {
        Long userId = requireUserId();
        String workspaceId = requireWorkspace();
        requireEditor(workspaceId, userId);
        return ApiResponse.ok(hotTopicService.promoteToTopicPool(workspaceId, userId, id,
                request == null ? null : request.getCategory(),
                request == null ? null : request.getTags()));
    }

    // ── 内部工具 ────────────────────────────────────────────────────────

    private Long writeImportLog(Long userId, String workspaceId, String filename,
                                int imported, int skipped, int errorRows, List<String> errors) {
        CsvImportLog log = new CsvImportLog();
        log.setUserId(userId);
        log.setWorkspaceId(workspaceId);
        log.setImportType("HOT_TOPIC");
        log.setFilename(filename);
        log.setTotalRows(imported + skipped + errorRows);
        log.setImportedRows(imported);
        log.setSkippedRows(skipped);
        log.setErrorRows(errorRows);
        log.setStatus(errors == null || errors.isEmpty() ? "completed" : "completed_with_errors");
        log.setErrorDetail(errors == null || errors.isEmpty() ? null : String.join("; ", errors));
        log.setCreatedAt(OffsetDateTime.now());
        csvImportLogMapper.insert(log);
        return log.getId();
    }

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

    /** ANALYST / EDITOR / ADMIN / OWNER */
    private void requireAnalyst(String workspaceId, Long userId) {
        String roleName = currentRoleName(workspaceId, userId);
        if (roleName != null && roleName.contains("ANALYST")) {
            return;
        }
        WorkspaceRole role = workspaceService.findRole(userId, workspaceId);
        if (role == null || !role.atLeast(WorkspaceRole.EDITOR)) {
            throw new BusinessException(CODE_FORBIDDEN, "forbidden");
        }
    }

    /** EDITOR / ADMIN / OWNER */
    private void requireEditor(String workspaceId, Long userId) {
        WorkspaceRole role = workspaceService.findRole(userId, workspaceId);
        if (role == null || !role.atLeast(WorkspaceRole.EDITOR)) {
            throw new BusinessException(CODE_FORBIDDEN, "forbidden");
        }
    }

    /** ADMIN / OWNER */
    private void requireAdmin(String workspaceId, Long userId) {
        WorkspaceRole role = workspaceService.findRole(userId, workspaceId);
        if (role == null || !role.atLeast(WorkspaceRole.ADMIN)) {
            throw new BusinessException(CODE_FORBIDDEN, "forbidden");
        }
    }

    /**
     * 取当前用户的原始角色名（保留 ANALYST 之类枚举外的角色）。
     */
    private String currentRoleName(String workspaceId, Long userId) {
        try {
            com.shortvideoscripagent.xhsagentyunying.domain.entity.WorkspaceMember member =
                    workspaceService.listMembers(workspaceId).stream()
                            .filter(m -> userId.equals(m.getUserId()))
                            .filter(m -> "active".equals(m.getStatus()))
                            .findFirst()
                            .orElse(null);
            return member == null ? null : member.getRole();
        } catch (Exception ex) {
            return null;
        }
    }
}
