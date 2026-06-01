package com.shortvideoscripagent.xhsagentyunying.ai.cover;

import com.shortvideoscripagent.xhsagentyunying.ai.AiRuntimePolicy;
import com.shortvideoscripagent.xhsagentyunying.ai.fixture.SampleAnalysisReport;
import com.shortvideoscripagent.xhsagentyunying.ai.model.ModelProvider;
import com.shortvideoscripagent.xhsagentyunying.ai.model.ModelProviderRegistry;
import com.shortvideoscripagent.xhsagentyunying.ai.parser.JsonReportParser;
import com.shortvideoscripagent.xhsagentyunying.config.AppAiProperties;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.AnalysisTask;
import com.shortvideoscripagent.xhsagentyunying.service.CoverStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoverAnalysisService {

    private final CoverStorageService coverStorageService;
    private final ModelProviderRegistry modelProviderRegistry;
    private final JsonReportParser jsonReportParser;
    private final AppAiProperties appProperties;
    private final AiRuntimePolicy aiRuntimePolicy;

    private volatile String coverVisionSystemPrompt;

    public Map<String, Object> analyze(AnalysisTask task) {
        if (task.getCoverImageUrl() == null || task.getCoverImageUrl().isBlank()) {
            return unavailable();
        }
        if (aiRuntimePolicy.useMockResponses()) {
            return SampleAnalysisReport.buildCoverAnalysis(task.getTitle(), task.getBody());
        }

        aiRuntimePolicy.assertRealAiAvailable();
        ModelProvider provider = modelProviderRegistry.getDefault();
        if (!provider.supportsVision()) {
            log.warn("Model provider {} does not support vision, skipping cover analysis", provider.id());
            return unavailable();
        }

        try {
            String objectKey = coverStorageService.extractObjectKey(task.getCoverImageUrl());
            CoverStorageService.StoredObject stored = coverStorageService.load(objectKey);
            String userPrompt = buildUserPrompt(task);
            int timeoutSeconds = appProperties.getAi().getAnalysisTimeoutSeconds();
            String raw = CompletableFuture
                    .supplyAsync(() -> provider.chatWithImage(coverVisionSystemPrompt(), userPrompt, stored.bytes(), stored.contentType()))
                    .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                    .join();
            return jsonReportParser.parseCoverAnalysis(raw);
        } catch (Exception ex) {
            log.warn("Cover vision analysis failed for task {}: {}", task.getId(), ex.getMessage());
            return unavailable();
        }
    }

    public void mergeCtrInsight(Map<String, Object> report, Map<String, Object> coverAnalysis) {
        if (report == null || coverAnalysis == null) {
            return;
        }
        if (!Boolean.TRUE.equals(coverAnalysis.get("available"))) {
            return;
        }
        Object ctrImpact = coverAnalysis.get("ctrImpact");
        if (ctrImpact == null || String.valueOf(ctrImpact).isBlank()) {
            return;
        }
        Object scoresObj = report.get("scores");
        if (!(scoresObj instanceof Map<?, ?> scores)) {
            return;
        }
        Object ctrObj = scores.get("ctr");
        if (!(ctrObj instanceof Map<?, ?> ctrMap)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> ctr = new LinkedHashMap<>((Map<String, Object>) ctrMap);
        String reason = String.valueOf(ctr.getOrDefault("reason", ""));
        String suffix = "封面：" + String.valueOf(ctrImpact).trim();
        if (reason.length() + suffix.length() > 80) {
            ctr.put("reason", suffix.length() <= 80 ? suffix : suffix.substring(0, 80));
        } else if (!reason.contains("封面")) {
            ctr.put("reason", (reason.isBlank() ? "" : reason + "；") + suffix);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> scoresCopy = new LinkedHashMap<>((Map<String, Object>) scores);
        scoresCopy.put("ctr", ctr);
        report.put("scores", scoresCopy);
    }

    private String buildUserPrompt(AnalysisTask task) {
        StringBuilder sb = new StringBuilder();
        sb.append("请分析这张小红书笔记封面图，结合笔记主题给出视觉运营点评。\n");
        if (task.getTitle() != null && !task.getTitle().isBlank()) {
            sb.append("【标题】").append(task.getTitle().trim()).append('\n');
        }
        if (task.getBody() != null && !task.getBody().isBlank()) {
            String body = task.getBody().trim();
            if (body.length() > 400) {
                body = body.substring(0, 400) + "…";
            }
            sb.append("【正文摘要】").append(body).append('\n');
        }
        return sb.toString();
    }

    private String coverVisionSystemPrompt() {
        if (coverVisionSystemPrompt != null) {
            return coverVisionSystemPrompt;
        }
        synchronized (this) {
            if (coverVisionSystemPrompt != null) {
                return coverVisionSystemPrompt;
            }
            try {
                ClassPathResource resource = new ClassPathResource("prompts/cover-vision-system.txt");
                coverVisionSystemPrompt = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            } catch (Exception ex) {
                coverVisionSystemPrompt = """
                        你是小红书封面视觉分析师。仅输出 JSON：
                        {"available":true,"keywords":[],"contrastComment":"","emotionMatch":"","ctrImpact":""}
                        """;
            }
            return coverVisionSystemPrompt;
        }
    }

    public static Map<String, Object> unavailable() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", false);
        result.put("keywords", java.util.List.of());
        result.put("contrastComment", "");
        result.put("emotionMatch", "");
        result.put("ctrImpact", "");
        return result;
    }
}
