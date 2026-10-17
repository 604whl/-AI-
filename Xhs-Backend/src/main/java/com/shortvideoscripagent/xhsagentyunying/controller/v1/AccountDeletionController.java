package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.retention.DeletionStatusResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.retention.ExportRequestResponse;
import com.shortvideoscripagent.xhsagentyunying.service.retention.AccountDeletionService;
import com.shortvideoscripagent.xhsagentyunying.service.retention.PersonalDataExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 账号注销与个人数据导出接口（P0-9）。
 * <p>
 * 路由前缀 /v1/auth（与 AuthController 同级）。
 * <ul>
 *   <li>DELETE  /v1/auth/me?confirm=DELETE        —— 30 天后注销</li>
 *   <li>POST     /v1/auth/me/cancel-deletion       —— 撤销注销</li>
 *   <li>GET      /v1/auth/me/deletion-status       —— 查询注销状态</li>
 *   <li>GET      /v1/auth/me/export                —— 申请个人数据导出（zip）</li>
 *   <li>GET      /v1/auth/me/exports               —— 导出历史</li>
 *   <li>GET      /v1/auth/me/exports/{id}          —— 导出详情/状态</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AccountDeletionController {

    private static final int CODE_UNAUTHORIZED = 40101;
    private static final int CODE_BAD_REQUEST = 40001;

    private final AccountDeletionService accountDeletionService;
    private final PersonalDataExportService personalDataExportService;

    /**
     * 申请账号注销（30 天软删）。
     * DELETE /v1/auth/me?confirm=DELETE
     */
    @DeleteMapping("/me")
    public ApiResponse<DeletionStatusResponse> requestDeletion(
            @RequestParam(name = "confirm", required = false) String confirm,
            @RequestParam(name = "confirmPhrase", required = false) String confirmPhrase) {
        Long userId = requireUserId();
        String phrase = (confirm != null && !confirm.isBlank()) ? confirm : confirmPhrase;
        if (phrase == null || phrase.isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "confirm_or_confirmPhrase_required");
        }
        String ip = RequestContext.getRequestIp();
        DeletionStatusResponse resp = accountDeletionService.requestDeletion(userId, phrase, ip);
        log.info("User {} requested account deletion, status={}", userId, resp.getDeletionStatus());
        return ApiResponse.ok(resp);
    }

    /**
     * 撤销账号注销。
     * POST /v1/auth/me/cancel-deletion
     */
    @PostMapping("/me/cancel-deletion")
    public ApiResponse<DeletionStatusResponse> cancelDeletion() {
        Long userId = requireUserId();
        DeletionStatusResponse resp = accountDeletionService.cancelDeletion(userId);
        log.info("User {} cancelled account deletion, status={}", userId, resp.getDeletionStatus());
        return ApiResponse.ok(resp);
    }

    /**
     * 查询当前注销状态。
     * GET /v1/auth/me/deletion-status
     */
    @GetMapping("/me/deletion-status")
    public ApiResponse<DeletionStatusResponse> getDeletionStatus() {
        Long userId = requireUserId();
        return ApiResponse.ok(accountDeletionService.getStatus(userId));
    }

    /**
     * 申请个人数据导出。
     * GET /v1/auth/me/export
     */
    @GetMapping("/me/export")
    public ApiResponse<ExportRequestResponse> requestExport(
            @RequestParam(name = "format", defaultValue = "zip") String format) {
        Long userId = requireUserId();
        if (!"zip".equalsIgnoreCase(format)) {
            throw new BusinessException(CODE_BAD_REQUEST, "unsupported_format:_only_zip_is_supported");
        }
        ExportRequestResponse resp = personalDataExportService.requestPersonalExport(userId);
        log.info("User {} requested personal data export, id={}", userId, resp.getId());
        return ApiResponse.ok(resp);
    }

    /**
     * 查询导出历史。
     * GET /v1/auth/me/exports
     */
    @GetMapping("/me/exports")
    public ApiResponse<List<ExportRequestResponse>> listExports() {
        Long userId = requireUserId();
        return ApiResponse.ok(personalDataExportService.listMyExports(userId));
    }

    /**
     * 查询单个导出状态。
     * GET /v1/auth/me/exports/{exportId}
     */
    @GetMapping("/me/exports/{exportId}")
    public ApiResponse<ExportRequestResponse> getExportStatus(@PathVariable UUID exportId) {
        Long userId = requireUserId();
        return ApiResponse.ok(personalDataExportService.getExportStatus(exportId, userId));
    }

    // ── private ─────────────────────────────────────────────────────────────

    private Long requireUserId() {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        }
        return userId;
    }
}
