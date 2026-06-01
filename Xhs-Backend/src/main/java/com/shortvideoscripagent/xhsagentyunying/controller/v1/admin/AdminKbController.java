package com.shortvideoscripagent.xhsagentyunying.controller.v1.admin;

import com.shortvideoscripagent.xhsagentyunying.ai.kb.KbChunkDraft;
import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.kb.KbChunkPreviewResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.kb.KbIngestResult;
import com.shortvideoscripagent.xhsagentyunying.service.KnowledgeIngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * 知识库入库（仅 local 环境）：Spring AI 切片 + Embedding 写入 pgvector。
 */
@Profile("local")
@RestController
@RequestMapping("/v1/admin/kb")
@RequiredArgsConstructor
@Tag(name = "Admin KB", description = "知识库入库（local）")
public class AdminKbController {

    private final KnowledgeIngestService knowledgeIngestService;

    @PostMapping("/reindex")
    @Operation(summary = "从 app.kb.source-dir 重新入库")
    public ApiResponse<KbIngestResult> reindex() throws IOException {
        return ApiResponse.ok(knowledgeIngestService.reindexFromSourceDir());
    }

    @GetMapping("/preview")
    @Operation(summary = "预览单文档切片（不写库）")
    public ApiResponse<List<KbChunkPreviewResponse>> preview(@RequestParam String docId) throws IOException {
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
