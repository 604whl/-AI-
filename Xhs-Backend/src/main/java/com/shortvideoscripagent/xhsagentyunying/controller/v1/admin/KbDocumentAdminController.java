package com.shortvideoscripagent.xhsagentyunying.controller.v1.admin;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.PaginatedResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.kb.KbDocumentDeleteResult;
import com.shortvideoscripagent.xhsagentyunying.dto.kb.KbDocumentDetail;
import com.shortvideoscripagent.xhsagentyunying.dto.kb.KbDocumentIndexStatus;
import com.shortvideoscripagent.xhsagentyunying.dto.kb.KbDocumentSearchHit;
import com.shortvideoscripagent.xhsagentyunying.dto.kb.KbDocumentSummary;
import com.shortvideoscripagent.xhsagentyunying.service.KbDocumentAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * S3-11 知识库文档管理接口。
 * <p>
 * 实际 URL 为 {@code /api/v1/admin/kb/documents}（{@code server.servlet.context-path=/api}）。
 * </p>
 * <p>
 * 权限：全部接口要求当前 workspace 内具备 ADMIN 及以上角色（OWNER 天然满足）。
 * 知识库文档为全局共享，列表 / 检索<b>不按 workspace 过滤</b>。
 * </p>
 */
@RestController
@RequestMapping("/v1/admin/kb/documents")
@RequiredArgsConstructor
@Tag(name = "Admin KB Documents", description = "知识库文档管理（列表/详情/检索/索引状态/删除）")
public class KbDocumentAdminController {

    private final KbDocumentAdminService kbDocumentAdminService;

    @GetMapping
    @Operation(summary = "分页列出知识库文档（按 doc_id 聚合）")
    public ApiResponse<PaginatedResponse<KbDocumentSummary>> list(
            @Parameter(description = "文档类型") @RequestParam(required = false) String docType,
            @Parameter(description = "内容类型") @RequestParam(required = false) String contentType,
            @Parameter(description = "doc_id / title 关键字") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数，最大 100") @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(kbDocumentAdminService.list(docType, contentType, keyword, page, size));
    }

    @GetMapping("/search")
    @Operation(summary = "按关键字检索知识库文档（title / content ILIKE）")
    public ApiResponse<List<KbDocumentSearchHit>> search(
            @Parameter(description = "检索关键字", required = true) @RequestParam String keyword,
            @Parameter(description = "限定文档类型，逗号分隔，可空") @RequestParam(required = false) String docTypes,
            @Parameter(description = "返回条数，最大 100") @RequestParam(defaultValue = "20") int limit
    ) {
        List<String> typeList = parseDocTypes(docTypes);
        return ApiResponse.ok(kbDocumentAdminService.search(keyword, typeList, limit));
    }

    @GetMapping("/{docId}")
    @Operation(summary = "查询单个知识库文档的 chunk 明细")
    public ApiResponse<KbDocumentDetail> detail(@PathVariable String docId) {
        return ApiResponse.ok(kbDocumentAdminService.getDocument(docId));
    }

    @GetMapping("/{docId}/index-status")
    @Operation(summary = "查询单个知识库文档的索引状态")
    public ApiResponse<KbDocumentIndexStatus> indexStatus(@PathVariable String docId) {
        return ApiResponse.ok(kbDocumentAdminService.indexStatus(docId));
    }

    @DeleteMapping("/{docId}")
    @Operation(summary = "删除知识库文档（全部 chunk + 唯一匹配的源文件）")
    public ApiResponse<KbDocumentDeleteResult> delete(@PathVariable String docId) {
        return ApiResponse.ok(kbDocumentAdminService.deleteDocument(docId));
    }

    private static List<String> parseDocTypes(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }
}
