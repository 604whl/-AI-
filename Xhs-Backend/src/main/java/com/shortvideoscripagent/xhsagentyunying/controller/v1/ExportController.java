package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.api.WorkspaceContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.export.ExportListItem;
import com.shortvideoscripagent.xhsagentyunying.dto.export.ExportResponse;
import com.shortvideoscripagent.xhsagentyunying.infra.ratelimit.RateLimited;
import com.shortvideoscripagent.xhsagentyunying.service.ExportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 导出 Controller（P0-5）。
 * <p>
 * 前缀：/v1
 * </p>
 *
 * <h3>API 概览</h3>
 * <ul>
 *   <li>GET  /v1/assets/{id}/export?format=md|docx|zip     — 异步导出（返回 exportId）</li>
 *   <li>GET  /v1/workspaces/{id}/export.csv?assetIds=...  — CSV 同步导出</li>
 *   <li>GET  /v1/exports/{id}                             — 查询导出状态</li>
 *   <li>GET  /v1/exports/{id}/download                   — 重定向到签名 URL</li>
 *   <li>GET  /v1/workspaces/{id}/exports                 — 导出历史列表</li>
 * </ul>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ExportController {

    private static final int CODE_UNAUTHORIZED = 40101;
    private static final int CODE_BAD_REQUEST = 40020;

    private final ExportService exportService;

    // ════════════════════════════════════════════════════════════════════════
    // 异步导出（MD / DOCX / ZIP）
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 异步创建导出任务。
     * POST 兼容 GET：支持 query param format=md|docx|zip
     */
    @PostMapping("/v1/assets/{id}/export")
    @RateLimited(key = "export:${workspaceId}", limit = 50, window = 86400, dimension = "workspace")
    public ApiResponse<ExportResponse> enqueueExport(
            @PathVariable("id") String assetId,
            @RequestParam(defaultValue = "md") String format
    ) {
        Long userId = requireUserId();
        UUID wsId = requireWorkspaceId();
        UUID assetUuid = parseUuid(assetId);

        validateFormat(format);
        ExportResponse resp = exportService.enqueueExport(wsId, userId, assetUuid, format);
        return ApiResponse.ok(resp);
    }

    /**
     * GET 别名（兼容前端习惯）。
     */
    @GetMapping("/v1/assets/{id}/export")
    @RateLimited(key = "export:${workspaceId}", limit = 50, window = 86400, dimension = "workspace")
    public ApiResponse<ExportResponse> getExportUrl(
            @PathVariable("id") String assetId,
            @RequestParam(defaultValue = "md") String format
    ) {
        return enqueueExport(assetId, format);
    }

    // ════════════════════════════════════════════════════════════════════════
    // CSV 同步导出
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 同步导出 CSV（批量资产列表）。
     * 直接返回文件流。
     */
    @GetMapping("/v1/workspaces/{id}/export.csv")
    @RateLimited(key = "export:${workspaceId}", limit = 50, window = 86400, dimension = "workspace")
    public void exportCsv(
            @PathVariable("id") String workspaceId,
            @RequestParam(required = false) String assetIds,
            HttpServletResponse response
    ) throws IOException {
        Long userId = requireUserId();
        UUID wsId = parseUuid(workspaceId);

        List<UUID> ids = null;
        if (assetIds != null && !assetIds.isBlank()) {
            ids = Arrays.stream(assetIds.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(this::parseUuid)
                    .toList();
        }

        byte[] csvBytes = exportService.exportCsv(wsId, userId, ids);

        String filename = "xhs-assets-" + java.time.LocalDate.now() + ".csv";
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + URLEncoder.encode(filename, StandardCharsets.UTF_8) + "\"");
        response.setContentLength(csvBytes.length);
        response.getOutputStream().write(csvBytes);
        response.getOutputStream().flush();
    }

    // ════════════════════════════════════════════════════════════════════════
    // 导出状态 / 下载
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 查询导出状态。
     */
    @GetMapping("/v1/exports/{id}")
    public ApiResponse<ExportResponse> getExportStatus(@PathVariable("id") String exportId) {
        Long userId = requireUserId();
        UUID wsId = requireWorkspaceId();
        UUID id = parseUuid(exportId);

        ExportResponse resp = exportService.getExportStatus(id, userId, wsId);
        return ApiResponse.ok(resp);
    }

    /**
     * 获取下载 URL 并重定向。
     */
    @GetMapping("/v1/exports/{id}/download")
    public void downloadExport(
            @PathVariable("id") String exportId,
            HttpServletResponse response
    ) throws IOException {
        Long userId = requireUserId();
        UUID wsId = requireWorkspaceId();
        UUID id = parseUuid(exportId);

        String signedUrl = exportService.getDownloadUrl(id, userId, wsId);

        // 302 重定向到签名 URL
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", signedUrl);
        response.getWriter().flush();
    }

    /**
     * 导出历史列表（分页）。
     */
    @GetMapping("/v1/workspaces/{id}/exports")
    public ApiResponse<List<ExportListItem>> listExports(
            @PathVariable("id") String workspaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        UUID wsId = parseUuid(workspaceId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        PageRequest pageable = PageRequest.of(safePage - 1, safeSize);

        List<ExportListItem> items = exportService.listExports(wsId, pageable);
        return ApiResponse.ok(items);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 私有方法
    // ════════════════════════════════════════════════════════════════════════

    private Long requireUserId() {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        }
        return userId;
    }

    private UUID requireWorkspaceId() {
        String wsId = WorkspaceContext.getWorkspaceId();
        if (wsId == null || wsId.isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "workspace_id_required");
        }
        return parseUuid(wsId);
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception e) {
            throw new BusinessException(40010, "invalid_id");
        }
    }

    private void validateFormat(String format) {
        List<String> valid = List.of("md", "docx", "zip");
        if (!valid.contains(format)) {
            throw new BusinessException(CODE_BAD_REQUEST,
                    "invalid_format: must be one of md, docx, zip");
        }
    }
}
