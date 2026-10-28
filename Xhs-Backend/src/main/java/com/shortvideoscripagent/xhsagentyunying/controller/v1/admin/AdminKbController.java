package com.shortvideoscripagent.xhsagentyunying.controller.v1.admin;

import com.shortvideoscripagent.xhsagentyunying.ai.kb.KbChunkDraft;
import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.config.AppKbProperties;
import com.shortvideoscripagent.xhsagentyunying.dto.kb.KbChunkPreviewResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.kb.KbIngestResult;
import com.shortvideoscripagent.xhsagentyunying.service.KbDocumentAdminService;
import com.shortvideoscripagent.xhsagentyunying.service.KnowledgeIngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 知识库入库：Spring AI 切片 + Embedding 写入 pgvector。
 * <p>
 * S3-11：原先受 {@code @Profile("local")} 限制，已解除，使阶段 3 的复盘回写审批流
 * 在生产 profile 下也能落地。同时补齐：
 * <ul>
 *   <li>ADMIN / OWNER 权限校验（入库与预览都是重操作 / 可窥探内部资料）；</li>
 *   <li>源目录不存在时优雅降级（返回"跳过"提示，而不是 500）。</li>
 * </ul>
 * </p>
 * <p>
 * 注意：{@code AdminKbController} 与 {@code KbDocumentAdminController} 的路径前缀不同
 * （{@code /v1/admin/kb} vs {@code /v1/admin/kb/documents}），Spring 的路径匹配是精确的，
 * 因此 {@code /v1/admin/kb/documents} 只会命中后者。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/v1/admin/kb")
@RequiredArgsConstructor
@Tag(name = "Admin KB", description = "知识库入库与切片预览")
public class AdminKbController {

    private final KnowledgeIngestService knowledgeIngestService;
    private final KbDocumentAdminService kbDocumentAdminService;
    private final AppKbProperties kbProperties;

    @PostMapping("/reindex")
    @Operation(summary = "从 app.kb.source-dir 重新入库（源目录不存在时优雅跳过）")
    public ApiResponse<KbIngestResult> reindex() throws IOException {
        kbDocumentAdminService.requireKbAdmin();

        Path sourceDir = Path.of(kbProperties.getSourceDir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceDir)) {
            log.warn("KB reindex skipped: source dir does not exist: {}", sourceDir);
            return ApiResponse.ok(new KbIngestResult(0, 0, 0,
                    List.of("源目录不存在，已跳过入库：" + sourceDir)));
        }
        return ApiResponse.ok(knowledgeIngestService.reindexFromSourceDir());
    }

    @GetMapping("/preview")
    @Operation(summary = "预览单文档切片（不写库）")
    public ApiResponse<List<KbChunkPreviewResponse>> preview(@RequestParam String docId) throws IOException {
        kbDocumentAdminService.requireKbAdmin();

        List<KbChunkDraft> chunks = knowledgeIngestService.previewSplit(docId);
        List<KbChunkPreviewResponse> response = chunks.stream()
                .map(c -> new KbChunkPreviewResponse(
                        c.docId(),
                        c.chunkType(),
                        c.content(),
                        c.content() == null ? 0 : c.content().length()
                ))
                .toList();
        return ApiResponse.ok(response);
    }
}
