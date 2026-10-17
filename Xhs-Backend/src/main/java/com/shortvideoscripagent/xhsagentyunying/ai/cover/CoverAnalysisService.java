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

    private volatile String coverSystemPrompt;

    public Map<String, Object> analyzeByContext(String coverImageUrl, String title, String body) {
        if (coverImageUrl == null || coverImageUrl.isBlank()) {
            return unavailable("not_provided");
        }
        AnalysisTask task = new AnalysisTask();
        task.setCoverImageUrl(coverImageUrl);
        task.setTitle(title);
        task.setBody(body);
        return analyze(task);
    }

    public Map<String, Object> analyze(AnalysisTask task) {
        if (task.getCoverImageUrl() == null || task.getCoverImageUrl().isBlank()) {
            return unavailable("not_provided");
        }
        if (aiRuntimePolicy.useMockResponses()) {
            return SampleAnalysisReport.buildCoverAnalysis(task.getTitle(), task.getBody());
        }

        aiRuntimePolicy.assertRealAiAvailable();
        ModelProvider provider = modelProviderRegistry.getDefault();

        try {
            String objectKey = coverStorageService.extractObjectKey(task.getCoverImageUrl());
            CoverStorageService.StoredObject stored = coverStorageService.load(objectKey);
            String userPrompt = buildUserPrompt(task, stored);
            int timeoutSeconds = appProperties.getAi().getAnalysisTimeoutSeconds();
            String raw = CompletableFuture
                    .supplyAsync(() -> provider.chat(coverSystemPrompt(), userPrompt))
                    .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                    .join();
            return jsonReportParser.parseCoverAnalysis(raw);
        } catch (Exception ex) {
            log.warn("Cover analysis failed for task {}: {}", task.getId(), ex.getMessage());
            return unavailable("cover_analysis_failed");
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

    private String buildUserPrompt(AnalysisTask task, CoverStorageService.StoredObject stored) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户已上传小红书笔记封面图，请结合笔记文案给出封面运营建议。\n");
        sb.append("【封面文件】类型=")
                .append(stored.contentType() == null ? "unknown" : stored.contentType())
                .append("，大小=")
                .append(stored.bytes() == null ? 0 : stored.bytes().length)
                .append("字节\n");
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

    private String coverSystemPrompt() {
        if (coverSystemPrompt != null) {
            return coverSystemPrompt;
        }
        synchronized (this) {
            if (coverSystemPrompt != null) {
                return coverSystemPrompt;
            }
            try {
                ClassPathResource resource = new ClassPathResource("prompts/cover-analysis-system.txt");
                coverSystemPrompt = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            } catch (Exception ex) {
                coverSystemPrompt = """
                        你是小红书封面运营分析师。仅输出 JSON：
                        {"available":true,"keywords":[],"contrastComment":"","emotionMatch":"","ctrImpact":""}
                        """;
            }
            return coverSystemPrompt;
        }
    }

    public static Map<String, Object> unavailable() {
        return unavailable("unavailable");
    }

    /**
     * P0-1: 从 URL 直接分析封面（不依赖 AnalysisTask）。
     */
    public Map<String, Object> analyzeFromPayload(String coverImageUrl) {
        if (coverImageUrl == null || coverImageUrl.isBlank()) {
            return unavailable("not_provided");
        }
        if (aiRuntimePolicy.useMockResponses()) {
            return SampleAnalysisReport.buildCoverAnalysis(null, null);
        }
        aiRuntimePolicy.assertRealAiAvailable();
        try {
            String objectKey = coverStorageService.extractObjectKey(coverImageUrl);
            CoverStorageService.StoredObject stored = coverStorageService.load(objectKey);
            // 最小化 prompt
            String userPrompt = "请分析以下封面图：" + stored.bytes().length + " 字节";
            int timeoutSeconds = appProperties.getAi().getAnalysisTimeoutSeconds();
            ModelProvider provider = modelProviderRegistry.getDefault();
            String raw = CompletableFuture
                    .supplyAsync(() -> provider.chat(coverSystemPrompt(), userPrompt))
                    .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                    .join();
            return jsonReportParser.parseCoverAnalysis(raw);
        } catch (Exception ex) {
            log.warn("Cover analysis from payload failed: {}", ex.getMessage());
            return unavailable("cover_analysis_failed");
        }
    }

    public static Map<String, Object> unavailable(String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", false);
        result.put("reason", reason);
        result.put("keywords", java.util.List.of());
        result.put("contrastComment", "");
        result.put("emotionMatch", "");
        result.put("ctrImpact", "");
        return result;
    }
}
