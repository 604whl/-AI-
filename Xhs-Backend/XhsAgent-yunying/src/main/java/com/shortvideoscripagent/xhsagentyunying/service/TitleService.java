package com.shortvideoscripagent.xhsagentyunying.service;

import com.shortvideoscripagent.xhsagentyunying.ai.fixture.SampleAnalysisReport;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.AnalysisTask;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.AnalysisTaskMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.title.TitleGenerateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.title.TitleGenerateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TitleService {

    private static final int CODE_BAD_REQUEST = 40001;
    private static final int CODE_NOT_FOUND = 40401;

    private final AnalysisTaskMapper taskMapper;

    public TitleGenerateResponse generate(Long userId, TitleGenerateRequest request) {
        TitleGenerateRequest effective = resolveContext(userId, request);
        validate(effective);

        int count = effective.getCount() == null ? 8 : effective.getCount();
        if (count < 5) {
            count = 5;
        } else if (count > 10) {
            count = 10;
        }

        return TitleGenerateResponse.builder()
                .analysisId(effective.getAnalysisId())
                .goal(effective.getGoal())
                .promptVersion("title-1.0.0")
                .titles(SampleAnalysisReport.buildTitles(
                        effective.getGoal(),
                        effective.getTitle(),
                        effective.getBody(),
                        count
                ))
                .build();
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
