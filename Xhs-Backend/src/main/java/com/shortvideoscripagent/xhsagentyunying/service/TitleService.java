package com.shortvideoscripagent.xhsagentyunying.service;

import com.shortvideoscripagent.xhsagentyunying.ai.AiRuntimePolicy;
import com.shortvideoscripagent.xhsagentyunying.ai.fixture.SampleAnalysisReport;
import com.shortvideoscripagent.xhsagentyunying.ai.model.ModelProviderRegistry;
import com.shortvideoscripagent.xhsagentyunying.ai.parser.JsonReportParser;
import com.shortvideoscripagent.xhsagentyunying.ai.prompt.PromptEngine;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.config.AppAiProperties;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.AnalysisTask;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.AnalysisTaskMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.title.TitleGenerateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.title.TitleGenerateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TitleService {

    private static final int CODE_BAD_REQUEST = 40001;
    private static final int CODE_NOT_FOUND = 40401;

    private final AnalysisTaskMapper taskMapper;
    private final ModelProviderRegistry modelProviderRegistry;
    private final PromptEngine promptEngine;
    private final JsonReportParser jsonReportParser;
    private final AppAiProperties appProperties;
    private final AiRuntimePolicy aiRuntimePolicy;

    public TitleGenerateResponse generate(Long userId, TitleGenerateRequest request) {
        TitleGenerateRequest effective = resolveContext(userId, request);
        validate(effective);

        int count = effective.getCount() == null ? 8 : effective.getCount();
        if (count < 5) {
            count = 5;
        } else if (count > 10) {
            count = 10;
        }

        List<TitleGenerateResponse.TitleItem> titles = aiRuntimePolicy.useMockResponses()
                ? SampleAnalysisReport.buildTitles(
                        effective.getGoal(),
                        effective.getTitle(),
                        effective.getBody(),
                        count
                )
                : generateWithLlm(effective, count);

        return TitleGenerateResponse.builder()
                .analysisId(effective.getAnalysisId())
                .goal(effective.getGoal())
                .promptVersion(PromptEngine.TITLE_PROMPT_VERSION)
                .titles(titles)
                .build();
    }

    private List<TitleGenerateResponse.TitleItem> generateWithLlm(TitleGenerateRequest request, int count) {
        aiRuntimePolicy.assertRealAiAvailable();
        String userPrompt = promptEngine.buildTitleGenerateUserPrompt(request, count);
        int timeoutSeconds = appProperties.getAi().getAnalysisTimeoutSeconds();

        String raw = CompletableFuture
                .supplyAsync(() -> modelProviderRegistry.chatWithFallback(promptEngine.titleSystemPrompt(), userPrompt).content())
                .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .join();
        return jsonReportParser.parseTitleGenerate(raw, count, count);
    }

    private TitleGenerateRequest resolveContext(Long userId, TitleGenerateRequest request) {
        if (request.getAnalysisId() == null || request.getAnalysisId().isBlank()) {
            return request;
        }

        AnalysisTask task = taskMapper.selectById(request.getAnalysisId());
        if (task == null || !task.getUserId().equals(userId)) {
            throw new BusinessException(CODE_NOT_FOUND, "analysis not found");
        }

        if (request.getTitle() == null || request.getTitle().isBlank()) {
            request.setTitle(task.getTitle());
        }
        if (request.getBody() == null || request.getBody().isBlank()) {
            request.setBody(task.getBody());
        }
        if (request.getPersona() == null || request.getPersona().isBlank()) {
            request.setPersona(task.getPersona());
        }
        return request;
    }

    private void validate(TitleGenerateRequest request) {
        if (request.getAnalysisId() != null && !request.getAnalysisId().isBlank()) {
            boolean hasTitle = request.getTitle() != null && !request.getTitle().isBlank();
            boolean hasBody = request.getBody() != null && request.getBody().length() >= 10;
            if (!hasTitle && !hasBody) {
                throw new BusinessException(CODE_BAD_REQUEST, "analysis has no title or body context");
            }
            return;
        }
        boolean hasTitle = request.getTitle() != null && !request.getTitle().isBlank();
        boolean hasBody = request.getBody() != null && request.getBody().length() >= 10;
        if (!hasTitle && !hasBody) {
            throw new BusinessException(CODE_BAD_REQUEST, "title or body is required");
        }
    }

}
