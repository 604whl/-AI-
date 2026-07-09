package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.AnalysisCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.AnalysisCreateResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.AnalysisDetailResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.AnalysisListItemResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.BodyGenerateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.BodyGenerateResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.OptimizeDraftRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.OptimizeDraftResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.PaginatedResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.title.TitleGenerateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.title.TitleGenerateResponse;
import com.shortvideoscripagent.xhsagentyunying.service.AnalysisAppService;
import com.shortvideoscripagent.xhsagentyunying.service.TitleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private static final int CODE_UNAUTHORIZED = 40101;

    private final AnalysisAppService analysisAppService;
    private final TitleService titleService;

    @PostMapping
    public ApiResponse<AnalysisCreateResponse> create(@Valid @RequestBody AnalysisCreateRequest request) {
        Long userId = requireUserId();
        return ApiResponse.ok(analysisAppService.create(userId, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<AnalysisDetailResponse> getById(@PathVariable String id) {
        Long userId = requireUserId();
        return ApiResponse.ok(analysisAppService.getById(userId, id));
    }

    /**
     * SSE 推送分析任务进度（phase / status），完成后发送 {@code done} 事件。
     */
    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamProgress(@PathVariable String id) {
        Long userId = requireUserId();
        return analysisAppService.streamProgress(userId, id);
    }

    @GetMapping
    public ApiResponse<PaginatedResponse<AnalysisListItemResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String scenario,
            @RequestParam(required = false) String keyword
    ) {
        Long userId = requireUserId();
        return ApiResponse.ok(analysisAppService.list(userId, page, size, status, scenario, keyword));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        Long userId = requireUserId();
        analysisAppService.delete(userId, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/optimize-draft")
    public ApiResponse<OptimizeDraftResponse> optimizeDraft(
            @PathVariable String id,
            @RequestBody(required = false) OptimizeDraftRequest request
    ) {
        Long userId = requireUserId();
        OptimizeDraftRequest body = request == null ? new OptimizeDraftRequest() : request;
        return ApiResponse.ok(analysisAppService.optimizeDraft(userId, id, body));
    }

    @PostMapping("/{id}/titles")
    public ApiResponse<TitleGenerateResponse> generateTitles(
            @PathVariable String id,
            @Valid @RequestBody TitleGenerateRequest request
    ) {
        Long userId = requireUserId();
        request.setAnalysisId(id);
        return ApiResponse.ok(titleService.generate(userId, request));
    }

    @PostMapping("/{id}/body")
    public ApiResponse<BodyGenerateResponse> generateBody(
            @PathVariable String id,
            @Valid @RequestBody BodyGenerateRequest request
    ) {
        Long userId = requireUserId();
        return ApiResponse.ok(analysisAppService.generateBody(userId, id, request));
    }

    private Long requireUserId() {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        }
        return userId;
    }
}
