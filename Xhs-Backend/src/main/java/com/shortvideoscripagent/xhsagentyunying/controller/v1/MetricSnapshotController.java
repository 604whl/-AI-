package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.CsvImportLog;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.CsvImportLogMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.metrics.MetricCollectRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.metrics.MetricCollectResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.metrics.MetricSnapshotCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.metrics.MetricSnapshotResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.metrics.MetricSnapshotReviseRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.metrics.MetricSyncStatusResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.metrics.MetricTrendBucket;
import com.shortvideoscripagent.xhsagentyunying.service.MetricSnapshotService;
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
import java.util.UUID;

/**
 * S3-5 指标快照 API。
 * <p>
 * 实际路径：{@code server.servlet.context-path=/api} + {@code /v1/metric-snapshots}
 * → {@code /api/v1/metric-snapshots}。
 * </p>
 */
@RestController
@RequestMapping("/v1/metric-snapshots")
@RequiredArgsConstructor
@Tag(name = "Metric Snapshots", description = "指标快照与数据回流（阶段 3 S3-5）")
public class MetricSnapshotController {

    private static final int CODE_UNAUTHORIZED = 40101;
    private static final int CODE_FORBIDDEN = 40301;
    private static final int CODE_CSV_SCHEMA_INVALID = 40062;
    private static final int CODE_ASSET_NOT_FOUND = 40450;
    private static final int CODE_SNAPSHOT_NOT_FOUND = 40451;

    /** CSV 错误明细最大保留条数 */
    private static final int MAX_ERRORS = 10;

    private final MetricSnapshotService metricSnapshotService;
    private final CsvImportLogMapper csvImportLogMapper;
    private final WorkspaceService workspaceService;
    private final com.shortvideoscripagent.xhsagentyunying.domain.mapper.WorkspaceMemberMapper workspaceMemberMapper;

    // ── 手动录入 ────────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "手动录入指标快照",
            description = "ANALYST/EDITOR/ADMIN/OWNER；统一口径 10 键缺失补 0，平台差异进 metric_extra；"
                    + "错误码 400 METRIC_FIELDS_MISSING / 409 ACCOUNT_MISMATCH / 404 ASSET_NOT_FOUND")
    public ApiResponse<MetricSnapshotResponse> create(@Valid @RequestBody MetricSnapshotCreateRequest request) {
        Long userId = requireUserId();
        String workspaceId = requireWorkspace();
        requireAnalyst(workspaceId, userId);
        return ApiResponse.ok(metricSnapshotService.createManual(workspaceId, userId, request));
    }

    // ── 列表 ────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "列出指标快照", description = "全部角色可读；按当前 workspace 过滤")
    public ApiResponse<List<MetricSnapshotResponse>> list(
            @RequestParam(required = false) String assetId,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireUserId();
        String workspaceId = requireWorkspace();
        return ApiResponse.ok(metricSnapshotService.list(workspaceId, parseUuid(assetId, CODE_ASSET_NOT_FOUND,
                "asset_not_found"), platform, parseDateTime(from), parseDateTime(to), source, page, size));
    }

    // ── 详情（含修订链）────────────────────────────────────────────────

    @GetMapping("/{id}")
    @Operation(summary = "查询指标快照详情", description = "全部角色可读；含后续修订链；错误码 404 SNAPSHOT_NOT_FOUND")
    public ApiResponse<MetricSnapshotResponse> get(@PathVariable String id) {
        requireUserId();
        String workspaceId = requireWorkspace();
        return ApiResponse.ok(metricSnapshotService.getWithChain(workspaceId,
                parseUuid(id, CODE_SNAPSHOT_NOT_FOUND, "SNAPSHOT_NOT_FOUND")));
    }

    // ── 修订 ────────────────────────────────────────────────────────────

    @PostMapping("/{id}/revise")
    @Operation(summary = "修订指标快照",
            description = "ANALYST/EDITOR/ADMIN/OWNER；生成新快照（revised_from_id 指向原快照），不覆盖原快照；"
                    + "错误码 404 SNAPSHOT_NOT_FOUND")
    public ApiResponse<MetricSnapshotResponse> revise(@PathVariable String id,
                                                       @Valid @RequestBody MetricSnapshotReviseRequest request) {
        Long userId = requireUserId();
        String workspaceId = requireWorkspace();
        requireAnalyst(workspaceId, userId);
        return ApiResponse.ok(metricSnapshotService.revise(workspaceId, userId,
                parseUuid(id, CODE_SNAPSHOT_NOT_FOUND, "SNAPSHOT_NOT_FOUND"), request));
    }

    // ── CSV 导入 ────────────────────────────────────────────────────────

    @PostMapping("/import")
    @Operation(summary = "CSV 导入指标快照",
            description = "ANALYST+；multipart csv + platformCode/accountId；未知列直接拒绝；"
                    + "错误码 400 CSV_SCHEMA_INVALID")
    public ApiResponse<MetricImportResult> importCsv(@RequestParam("file") MultipartFile file,
                                                      @RequestParam String platformCode,
                                                      @RequestParam(required = false) String accountId) {
        Long userId = requireUserId();
        String workspaceId = requireWorkspace();
        requireAnalyst(workspaceId, userId);

        if (file == null || file.isEmpty()) {
            throw new BusinessException(CODE_CSV_SCHEMA_INVALID, "CSV_SCHEMA_INVALID: csv_file_empty");
        }
        String csvText;
        try {
            csvText = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new BusinessException(CODE_CSV_SCHEMA_INVALID, "CSV_SCHEMA_INVALID: cannot_read_csv");
        }

        List<String> errors = new ArrayList<>();
        MetricSnapshotService.CsvImportOutcome outcome;
        Long importLogId;
        try {
            outcome = metricSnapshotService.importMetricsCsv(
                    workspaceId, userId, platformCode, accountId, csvText, errors, MAX_ERRORS);
            importLogId = writeImportLog(userId, workspaceId, file.getOriginalFilename(),
                    outcome.imported(), outcome.skipped(), outcome.errorRows(), errors);
        } catch (BusinessException be) {
            // 表头非法等致命错误：先落 csv_import_log（含 error_detail 行号 + 原因），再抛出
            writeImportLog(userId, workspaceId, file.getOriginalFilename(), 0, 0, 1, errors);
            throw be;
        }

        return ApiResponse.ok(MetricImportResult.builder()
                .importLogId(importLogId)
                .totalRows(outcome.imported() + outcome.skipped() + outcome.errorRows())
                .importedRows(outcome.imported())
                .skippedRows(outcome.skipped())
                .errorRows(outcome.errorRows())
                .errors(errors)
                .build());
    }

    /**
     * 写入 csv_import_log（复用既有框架表）。
     *
     * @return 导入日志 ID
     */
    private Long writeImportLog(Long userId, String workspaceId, String filename,
                                int imported, int skipped, int errorRows, List<String> errors) {
        CsvImportLog log = new CsvImportLog();
        log.setUserId(userId);
        log.setWorkspaceId(workspaceId);
        log.setImportType("METRIC");
        log.setFilename(filename);
        log.setTotalRows(imported + skipped + errorRows);
        log.setImportedRows(imported);
        log.setSkippedRows(skipped);
        log.setErrorRows(errorRows);
        log.setStatus(errors.isEmpty() ? "completed" : "completed_with_errors");
        log.setErrorDetail(errors.isEmpty() ? null : String.join("; ", errors));
        log.setCreatedAt(OffsetDateTime.now());
        csvImportLogMapper.insert(log);
        return log.getId();
    }

    // ── 趋势 ────────────────────────────────────────────────────────────

    @GetMapping("/trend")
    @Operation(summary = "指标趋势",
            description = "全部角色可读；granularity=day|week；每个 asset+externalContentId 只取最新一条快照，修订不重复计入")
    public ApiResponse<List<MetricTrendBucket>> trend(
            @RequestParam(required = false) String assetId,
            @RequestParam(required = false) String platform,
            @RequestParam(defaultValue = "day") String granularity,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        requireUserId();
        String workspaceId = requireWorkspace();
        return ApiResponse.ok(metricSnapshotService.trend(workspaceId,
                parseUuid(assetId, CODE_ASSET_NOT_FOUND, "asset_not_found"),
                platform, granularity, parseDateTime(from), parseDateTime(to)));
    }

    // ── 采集触发 ────────────────────────────────────────────────────────

    @PostMapping("/collect")
    @Operation(summary = "触发指标采集",
            description = "ADMIN/OWNER；走持久化任务队列，不计费；错误码 403 CONNECTOR_NOT_AUTHORIZED")
    public ApiResponse<MetricCollectResponse> collect(@Valid @RequestBody MetricCollectRequest request) {
        Long userId = requireUserId();
        String workspaceId = requireWorkspace();
        requireAdmin(workspaceId, userId);
        return ApiResponse.ok(metricSnapshotService.collect(workspaceId, userId, request));
    }

    // ── 同步状态 ────────────────────────────────────────────────────────

    @GetMapping("/sync-status")
    @Operation(summary = "查询指标采集同步状态",
            description = "全部角色可读；含同步延迟 / 连续失败次数 / 最后成功时间")
    public ApiResponse<List<MetricSyncStatusResponse>> syncStatus(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String accountId) {
        requireUserId();
        String workspaceId = requireWorkspace();
        return ApiResponse.ok(metricSnapshotService.syncStatus(workspaceId, platform, accountId));
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
     * <p>
     * 项目现有 {@link WorkspaceRole} 枚举只有 OWNER / ADMIN / EDITOR / REVIEWER / VIEWER，
     * ANALYST 尚未纳入枚举，因此这里按"角色名包含 ANALYST 或层级 >= EDITOR"判定。
     * </p>
     */
    private void requireAnalyst(String workspaceId, Long userId) {
        String roleName = currentRoleName(workspaceId, userId);
        if (roleName == null) {
            throw new BusinessException(CODE_FORBIDDEN, "forbidden");
        }
        if (roleName.contains("ANALYST")) {
            return;
        }
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
                    workspaceMemberMapper.selectOne(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                                    com.shortvideoscripagent.xhsagentyunying.domain.entity.WorkspaceMember>()
                                    .eq(com.shortvideoscripagent.xhsagentyunying.domain.entity.WorkspaceMember::getWorkspaceId, workspaceId)
                                    .eq(com.shortvideoscripagent.xhsagentyunying.domain.entity.WorkspaceMember::getUserId, userId)
                                    .eq(com.shortvideoscripagent.xhsagentyunying.domain.entity.WorkspaceMember::getStatus, "active"));
            return member == null ? null : member.getRole();
        } catch (Exception ex) {
            return null;
        }
    }

    private UUID parseUuid(String raw, int notFoundCode, String message) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(notFoundCode, message);
        }
    }

    private OffsetDateTime parseDateTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * CSV 导入结果。
     */
    public static class MetricImportResult {
        private Long importLogId;
        private Integer totalRows;
        private Integer importedRows;
        private Integer skippedRows;
        private Integer errorRows;
        private List<String> errors;

        static MetricImportResultBuilder builder() {
            return new MetricImportResultBuilder();
        }

        public Long getImportLogId() {
            return importLogId;
        }

        public Integer getTotalRows() {
            return totalRows;
        }

        public Integer getImportedRows() {
            return importedRows;
        }

        public Integer getSkippedRows() {
            return skippedRows;
        }

        public Integer getErrorRows() {
            return errorRows;
        }

        public List<String> getErrors() {
            return errors;
        }

        /**
         * 轻量 builder（避免引入 Lombok 到内部类）。
         */
        static final class MetricImportResultBuilder {
            private final MetricImportResult target = new MetricImportResult();

            MetricImportResultBuilder importLogId(Long value) {
                target.importLogId = value;
                return this;
            }

            MetricImportResultBuilder totalRows(Integer value) {
                target.totalRows = value;
                return this;
            }

            MetricImportResultBuilder importedRows(Integer value) {
                target.importedRows = value;
                return this;
            }

            MetricImportResultBuilder skippedRows(Integer value) {
                target.skippedRows = value;
                return this;
            }

            MetricImportResultBuilder errorRows(Integer value) {
                target.errorRows = value;
                return this;
            }

            MetricImportResultBuilder errors(List<String> value) {
                target.errors = value;
                return this;
            }

            MetricImportResult build() {
                return target;
            }
        }
    }
}
