package com.shortvideoscripagent.xhsagentyunying.service.analysis;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shortvideoscripagent.xhsagentyunying.config.AppAiProperties;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.AnalysisTask;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.AnalysisTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisTaskRecoveryScheduler {

    private static final int CODE_STALE_TASK = 50403;
    private static final long RECOVERY_GRACE_SECONDS = 120L;

    private final AnalysisTaskMapper taskMapper;
    private final AppAiProperties appProperties;

    @Scheduled(fixedDelayString = "${app.ai.recovery-fixed-delay-ms:60000}")
    public void markStaleTasksFailed() {
        long staleAfterSeconds = appProperties.getAi().getAnalysisTimeoutSeconds() + RECOVERY_GRACE_SECONDS;
        OffsetDateTime staleBefore = OffsetDateTime.now().minusSeconds(staleAfterSeconds);

        LambdaUpdateWrapper<AnalysisTask> wrapper = new LambdaUpdateWrapper<AnalysisTask>()
                .in(AnalysisTask::getStatus, List.of("pending", "processing"))
                .lt(AnalysisTask::getUpdatedAt, staleBefore)
                .set(AnalysisTask::getStatus, "failed")
                .set(AnalysisTask::getFailureReason, "stale_task")
                .set(AnalysisTask::getFailureCode, CODE_STALE_TASK)
                .set(AnalysisTask::getUpdatedAt, OffsetDateTime.now());

        int updated = taskMapper.update(null, wrapper);
        if (updated > 0) {
            log.warn("Recovered {} stale analysis task(s) older than {} seconds", updated, staleAfterSeconds);
        }
    }
}
