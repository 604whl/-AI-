package com.shortvideoscripagent.xhsagentyunying.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.connector.MetricKeys;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.MetricComparison;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.MetricForecast;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.MetricSnapshot;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.RetrospectiveFeedback;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.RetrospectiveReport;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.MetricComparisonMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.MetricForecastMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.MetricSnapshotMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.RetrospectiveFeedbackMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.RetrospectiveReportMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.PaginatedResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.retrospective.RetrospectiveCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.retrospective.RetrospectiveCreateResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.retrospective.RetrospectiveFeedbackRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.retrospective.RetrospectiveFeedbackResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.retrospective.RetrospectiveFeedbackReviewRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.retrospective.RetrospectiveResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.retrospective.RetrospectiveSummaryResponse;
import com.shortvideoscripagent.xhsagentyunying.infra.audit.AuditWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * S3-7 复盘报告生成 + S3-8 回写审批服务。
 * <p>
 * <b>E3 出口条件硬约束</b>：报告必须产出 <b>≥ 3 条可执行建议</b>，
 * 且每条建议的 {@code evidenceRefs} <b>非空</b>并指向真实存在的
 * {@code metric_snapshot.id} / {@code metric_comparison.id}；
 * 无证据的建议在落库前被<b>丢弃</b>，不写入"看起来有依据"的假建议。
 * </p>
 * <p>
 * <b>降级而非伪成功</b>：数据不足（无预测 / 无快照 / 无对比行）时报告状态为
 * {@code INSUFFICIENT_DATA}，{@code degraded=true} 并给出 {@code degradedReason}，
 * <b>绝不</b>返回 COMPLETED 却拿不出依据。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrospectiveService {

    // ── 错误码 ──────────────────────────────────────────────────────────
    /** 400 复盘请求参数非法 */
    public static final int CODE_RETROSPECTIVE_INVALID = 40073;
    /** 404 复盘报告不存在 / 越权 */
    public static final int CODE_REPORT_NOT_FOUND = 40472;
    /** 404 回写记录不存在 / 越权 */
    public static final int CODE_FEEDBACK_NOT_FOUND = 40473;
    /** 409 回写已被审批 / 状态不允许 */
    public static final int CODE_FEEDBACK_STATE_CONFLICT = 40972;
    /** 400 驳回时缺少意见 */
    public static final int CODE_COMMENT_REQUIRED = 40075;

    /** 建议最少条数（E3 出口条件） */
    private static final int MIN_SUGGESTION_COUNT = 3;

    /** 允许的报表维度 */
    private static final Set<String> ALLOWED_SCOPE_TYPES = Set.of(
            RetrospectiveReport.SCOPE_ASSET,
            RetrospectiveReport.SCOPE_ACCOUNT,
            RetrospectiveReport.SCOPE_PLATFORM,
            RetrospectiveReport.SCOPE_CATEGORY,
            RetrospectiveReport.SCOPE_CAMPAIGN,
            RetrospectiveReport.SCOPE_PERIOD);

    private final RetrospectiveReportMapper reportMapper;
    private final RetrospectiveFeedbackMapper feedbackMapper;
    private final MetricForecastMapper forecastMapper;
    private final MetricSnapshotMapper snapshotMapper;
    private final MetricComparisonMapper comparisonMapper;
    private final TemplateEffectStatsService templateEffectStatsService;
    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;

    // ══════════════════════════════════════════════════════════════════
    // 生成复盘报告
    // ══════════════════════════════════════════════════════════════════

    /**
     * 生成复盘报告（同步）。
     * <p>
     * 数据来源：当前 workspace 下与 {@code assetId} 关联的预测、指标快照与偏差对比行。
     * </p>
     */
    @Transactional
    public RetrospectiveCreateResponse create(String workspaceId, Long userId,
                                              RetrospectiveCreateRequest request,
                                              String idempotencyKey) {
        requireWorkspace(workspaceId);

        String scopeType = upper(request.getScopeType());
        if (scopeType == null || !ALLOWED_SCOPE_TYPES.contains(scopeType)) {
            throw new BusinessException(CODE_RETROSPECTIVE_INVALID,
                    "RETROSPECTIVE_INVALID: scope_type_not_allowed");
        }

        UUID assetId = parseUuid(request.getAssetId());
        String platformCode = upper(request.getPlatformCode());

        // 幂等：同一 workspace 同键只生成一份报告
        String key = normalizeKey(idempotencyKey);
        if (key != null) {
            RetrospectiveReport existing = reportMapper.selectByIdempotencyKey(workspaceId, key);
            if (existing != null) {
                log.info("Retrospective create skipped (idempotent): ws={} key={}", workspaceId, key);
                return toCreateResponse(existing);
            }
        }

        OffsetDateTime now = OffsetDateTime.now();

        // ── 采集预测侧 ────────────────────────────────────────────────────
        MetricForecast forecast = forecastMapper.selectLatest(workspaceId, assetId, platformCode);
        Map<String, Long> predicted = forecast == null
                ? Map.of()
                : readCoreMetrics(forecast.getPredictedMetrics());

        // ── 采集实际侧 ────────────────────────────────────────────────────
        List<MetricSnapshot> snapshots = snapshotMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MetricSnapshot>()
                        .eq(MetricSnapshot::getWorkspaceId, workspaceId)
                        .eq(assetId != null, MetricSnapshot::getAssetId, assetId)
                        .orderByDesc(MetricSnapshot::getCollectedAt)
                        .orderByDesc(MetricSnapshot::getCreatedAt)
                        .last("LIMIT 20"));
        Map<String, Long> actual = aggregateLatest(snapshots);

        // ── 采集对比行 ────────────────────────────────────────────────────
        List<MetricComparison> comparisons = comparisonMapper.listAllForWorkspace(workspaceId, assetId);

        // ── 数据充分性判定（先判不足，绝不伪成功）────────────────────────
        List<String> missing = new ArrayList<>();
        if (predicted.isEmpty()) {
            missing.add("PREDICTION");
        }
        if (actual.isEmpty()) {
            missing.add("ACTUAL_METRICS");
        }
        if (comparisons.isEmpty()) {
            missing.add("COMPARISON");
        }

        String status;
        boolean degraded;
        String degradedReason;
        List<RetrospectiveResponse.RetrospectiveSuggestion> suggestions;
        List<RetrospectiveResponse.DimensionEvidence> hitDimensions = new ArrayList<>();
        List<RetrospectiveResponse.DimensionEvidence> missedDimensions = new ArrayList<>();

        if (!missing.isEmpty()) {
            status = RetrospectiveReport.STATUS_INSUFFICIENT_DATA;
            degraded = true;
            degradedReason = "数据不足，缺少：" + String.join("、", missing)
                    + "。请先完成预测创建、指标回流与偏差对比后再生成复盘。";
            suggestions = List.of();
        } else {
            // 命中 / 失败维度分离
            for (MetricComparison row : comparisons) {
                RetrospectiveResponse.DimensionEvidence evidence = RetrospectiveResponse.DimensionEvidence.builder()
                        .dimension(row.getDimension())
                        .evidence(buildEvidence(row))
                        .possibleReasons(MetricComparison.HIT_MISS.equals(row.getHit())
                                ? possibleReasons(row)
                                : List.of())
                        .build();
                if (MetricComparison.HIT_HIT.equals(row.getHit())) {
                    hitDimensions.add(evidence);
                } else {
                    missedDimensions.add(evidence);
                }
            }

            suggestions = buildSuggestions(comparisons, snapshots, forecast);

            if (suggestions.size() < MIN_SUGGESTION_COUNT) {
                // 建议不足 3 条 → 降级，绝不用"空建议"冒充完成
                status = RetrospectiveReport.STATUS_DEGRADED;
                degraded = true;
                degradedReason = "可追溯证据不足，仅生成 " + suggestions.size()
                        + " 条带证据建议（低于 E3 要求的 " + MIN_SUGGESTION_COUNT + " 条），"
                        + "请补充预测/快照/对比数据后重新生成。";
            } else {
                status = RetrospectiveReport.STATUS_COMPLETED;
                degraded = false;
                degradedReason = null;
            }
        }

        // ── 落库 ──────────────────────────────────────────────────────────
        RetrospectiveReport report = new RetrospectiveReport();
        report.setId(UUID.randomUUID());
        report.setWorkspaceId(workspaceId);
        report.setScopeType(scopeType);
        report.setScopeKey(resolveScopeKey(request, scopeType, assetId, platformCode));
        report.setAssetId(assetId);
        report.setAccountId(blankToNull(request.getAccountId()));
        report.setPlatformCode(platformCode);
        report.setCategoryCode(blankToNull(request.getCategoryCode()));
        report.setCampaignId(blankToNull(request.getCampaignId()));
        report.setPeriodStart(request.getPeriodStart());
        report.setPeriodEnd(request.getPeriodEnd());
        report.setSummary(buildSummary(status, predicted, actual, comparisons, suggestions, missing));
        report.setPredictions(writeJson(toSideMap(predicted, forecast == null ? null : forecast.getGeneratedAt())));
        report.setActuals(writeJson(toSideMap(actual, snapshots.isEmpty() ? null : snapshots.get(0).getCollectedAt())));
        report.setHitDimensions(writeJson(hitDimensions));
        report.setMissedDimensions(writeJson(missedDimensions));
        report.setSuggestions(writeJson(suggestions));
        report.setModelName(forecast == null ? null : forecast.getModelName());
        report.setPromptVersion(forecast == null ? null : forecast.getPromptVersion());
        report.setKbVersion(forecast == null ? null : forecast.getKbVersion());
        report.setGeneratedAt(now);
        report.setStatus(status);
        report.setDegraded(degraded);
        report.setDegradedReason(degradedReason);
        report.setMissingDimensions(writeJson(missing));
        report.setIdempotencyKey(key);
        report.setCreatedAt(now);

        try {
            reportMapper.insert(report);
        } catch (DuplicateKeyException ex) {
            if (key == null) {
                throw ex;
            }
            RetrospectiveReport existing = reportMapper.selectByIdempotencyKey(workspaceId, key);
            if (existing != null) {
                log.info("Retrospective create skipped (idempotent race): ws={} key={}", workspaceId, key);
                return toCreateResponse(existing);
            }
            throw ex;
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("reportId", report.getId().toString());
        meta.put("status", status);
        meta.put("suggestionCount", suggestions.size());
        meta.put("scopeType", scopeType);
        auditWriter.record("retrospective.create", "RETROSPECTIVE_REPORT", report.getId().toString(), meta);

        log.info("Retrospective created: id={} ws={} status={} suggestions={}",
                report.getId(), workspaceId, status, suggestions.size());
        return toCreateResponse(report);
    }

    // ══════════════════════════════════════════════════════════════════
    // 报告查询
    // ══════════════════════════════════════════════════════════════════

    /**
     * 分页列出复盘报告。
     */
    public PaginatedResponse<RetrospectiveSummaryResponse> list(String workspaceId,
                                                                  String scopeType,
                                                                  String platformCode,
                                                                  String assetId,
                                                                  OffsetDateTime from,
                                                                  OffsetDateTime to,
                                                                  int page, int size) {
        requireWorkspace(workspaceId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 200);
        long offset = (long) (safePage - 1) * safeSize;

        String scope = upper(scopeType);
        String platform = upper(platformCode);
        UUID asset = parseUuid(assetId);

        List<RetrospectiveSummaryResponse> items = reportMapper.listByWorkspace(workspaceId, scope,
                        platform, asset, from, to, offset, safeSize)
                .stream().map(this::toSummary).toList();
        long total = reportMapper.countByWorkspace(workspaceId, scope, platform, asset, from, to);
        return new PaginatedResponse<>(items, total, safePage, safeSize);
    }

    /**
     * 查询复盘报告详情。
     */
    public RetrospectiveResponse get(String workspaceId, UUID reportId) {
        requireWorkspace(workspaceId);
        RetrospectiveReport report = reportMapper.selectByIdAndWorkspace(reportId, workspaceId);
        if (report == null) {
            throw new BusinessException(CODE_REPORT_NOT_FOUND, "REPORT_NOT_FOUND");
        }
        return toResponse(report);
    }

    // ══════════════════════════════════════════════════════════════════
    // S3-8 回写审批
    // ══════════════════════════════════════════════════════════════════

    /**
     * 提交回写申请（初始状态 PENDING，<b>不写入</b>效果统计）。
     */
    @Transactional
    public RetrospectiveFeedbackResponse submitFeedback(String workspaceId, Long userId,
                                                        UUID reportId,
                                                        RetrospectiveFeedbackRequest request,
                                                        String idempotencyKey) {
        requireWorkspace(workspaceId);
        if (reportId == null) {
            throw new BusinessException(CODE_REPORT_NOT_FOUND, "REPORT_NOT_FOUND");
        }
        RetrospectiveReport report = reportMapper.selectByIdAndWorkspace(reportId, workspaceId);
        if (report == null) {
            throw new BusinessException(CODE_REPORT_NOT_FOUND, "REPORT_NOT_FOUND");
        }

        String targetType = upper(request.getTargetType());
        if (!RetrospectiveFeedback.TARGET_TEMPLATE.equals(targetType)
                && !RetrospectiveFeedback.TARGET_KB_DOCUMENT.equals(targetType)) {
            throw new BusinessException(CODE_RETROSPECTIVE_INVALID,
                    "RETROSPECTIVE_INVALID: target_type_not_allowed");
        }

        String key = normalizeKey(idempotencyKey);
        if (key != null) {
            RetrospectiveFeedback existing = feedbackMapper.selectByIdempotencyKey(workspaceId, key);
            if (existing != null) {
                log.info("Retrospective feedback skipped (idempotent): ws={} key={}", workspaceId, key);
                return toFeedbackResponse(existing);
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        RetrospectiveFeedback feedback = new RetrospectiveFeedback();
        feedback.setId(UUID.randomUUID());
        feedback.setReportId(reportId);
        feedback.setWorkspaceId(workspaceId);
        feedback.setTargetType(targetType);
        feedback.setTargetId(request.getTargetId().trim());
        feedback.setProposedContent(blankToNull(request.getProposedContent()));
        feedback.setProposedStats(writeJson(request.getProposedStats() == null ? Map.of() : request.getProposedStats()));
        // 初始 PENDING：此时<b>不</b>写效果统计
        feedback.setStatus(RetrospectiveFeedback.STATUS_PENDING);
        feedback.setCreatedBy(userId);
        feedback.setIdempotencyKey(key);
        feedback.setCreatedAt(now);

        try {
            feedbackMapper.insert(feedback);
        } catch (DuplicateKeyException ex) {
            if (key == null) {
                throw ex;
            }
            RetrospectiveFeedback existing = feedbackMapper.selectByIdempotencyKey(workspaceId, key);
            if (existing != null) {
                return toFeedbackResponse(existing);
            }
            throw ex;
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("feedbackId", feedback.getId().toString());
        meta.put("reportId", reportId.toString());
        meta.put("targetType", targetType);
        auditWriter.record("retrospective.feedback.submit", "RETROSPECTIVE_FEEDBACK",
                feedback.getId().toString(), meta);

        log.info("Retrospective feedback submitted: id={} report={} target={} status=PENDING",
                feedback.getId(), reportId, targetType);
        return toFeedbackResponse(feedback);
    }

    /**
     * 列出某报告下的全部回写记录。
     */
    public List<RetrospectiveFeedbackResponse> listFeedback(String workspaceId, UUID reportId) {
        requireWorkspace(workspaceId);
        if (reportId == null) {
            throw new BusinessException(CODE_REPORT_NOT_FOUND, "REPORT_NOT_FOUND");
        }
        if (reportMapper.selectByIdAndWorkspace(reportId, workspaceId) == null) {
            throw new BusinessException(CODE_REPORT_NOT_FOUND, "REPORT_NOT_FOUND");
        }
        return feedbackMapper.listByReport(workspaceId, reportId)
                .stream().map(this::toFeedbackResponse).toList();
    }

    /**
     * 审批通过：<b>此时才</b>真正写回效果统计。
     */
    @Transactional
    public RetrospectiveFeedbackResponse approve(String workspaceId, Long userId,
                                                  UUID feedbackId,
                                                  RetrospectiveFeedbackReviewRequest request) {
        requireWorkspace(workspaceId);
        RetrospectiveFeedback feedback = feedbackMapper.selectByIdAndWorkspace(feedbackId, workspaceId);
        if (feedback == null) {
            throw new BusinessException(CODE_FEEDBACK_NOT_FOUND, "FEEDBACK_NOT_FOUND");
        }
        if (!RetrospectiveFeedback.STATUS_PENDING.equals(feedback.getStatus())) {
            throw new BusinessException(CODE_FEEDBACK_STATE_CONFLICT,
                    "FEEDBACK_STATE_CONFLICT: not_pending");
        }

        OffsetDateTime now = OffsetDateTime.now();
        String comment = request == null ? null : blankToNull(request.getComment());

        int affected = feedbackMapper.review(feedbackId, workspaceId,
                RetrospectiveFeedback.STATUS_APPROVED, userId, comment, now);
        if (affected == 0) {
            throw new BusinessException(CODE_FEEDBACK_STATE_CONFLICT,
                    "FEEDBACK_STATE_CONFLICT: already_reviewed");
        }

        // 审批通过 → 写回效果统计（此前的 PENDING 阶段绝不写）
        applyEffect(workspaceId, feedback);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("feedbackId", feedbackId.toString());
        meta.put("targetType", feedback.getTargetType());
        meta.put("targetId", feedback.getTargetId());
        auditWriter.record("retrospective.feedback.approve", "RETROSPECTIVE_FEEDBACK",
                feedbackId.toString(), meta);

        RetrospectiveFeedback updated = feedbackMapper.selectByIdAndWorkspace(feedbackId, workspaceId);
        log.info("Retrospective feedback approved: id={} target={}/{}",
                feedbackId, feedback.getTargetType(), feedback.getTargetId());
        return toFeedbackResponse(updated);
    }

    /**
     * 审批驳回（意见必填）。
     */
    @Transactional
    public RetrospectiveFeedbackResponse reject(String workspaceId, Long userId,
                                                 UUID feedbackId,
                                                 RetrospectiveFeedbackReviewRequest request) {
        requireWorkspace(workspaceId);
        RetrospectiveFeedback feedback = feedbackMapper.selectByIdAndWorkspace(feedbackId, workspaceId);
        if (feedback == null) {
            throw new BusinessException(CODE_FEEDBACK_NOT_FOUND, "FEEDBACK_NOT_FOUND");
        }
        if (!RetrospectiveFeedback.STATUS_PENDING.equals(feedback.getStatus())) {
            throw new BusinessException(CODE_FEEDBACK_STATE_CONFLICT,
                    "FEEDBACK_STATE_CONFLICT: not_pending");
        }
        String comment = request == null ? null : blankToNull(request.getComment());
        if (comment == null) {
            throw new BusinessException(CODE_COMMENT_REQUIRED, "COMMENT_REQUIRED");
        }

        int affected = feedbackMapper.review(feedbackId, workspaceId,
                RetrospectiveFeedback.STATUS_REJECTED, userId, comment, null);
        if (affected == 0) {
            throw new BusinessException(CODE_FEEDBACK_STATE_CONFLICT,
                    "FEEDBACK_STATE_CONFLICT: already_reviewed");
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("feedbackId", feedbackId.toString());
        meta.put("targetType", feedback.getTargetType());
        meta.put("targetId", feedback.getTargetId());
        auditWriter.record("retrospective.feedback.reject", "RETROSPECTIVE_FEEDBACK",
                feedbackId.toString(), meta);

        RetrospectiveFeedback updated = feedbackMapper.selectByIdAndWorkspace(feedbackId, workspaceId);
        log.info("Retrospective feedback rejected: id={} target={}/{}",
                feedbackId, feedback.getTargetType(), feedback.getTargetId());
        return toFeedbackResponse(updated);
    }

    /**
     * 审批通过后写回效果统计。
     * <p>
     * 驳回 / PENDING 阶段<b>不</b>调用本方法 —— 这是"审批后才生效"的唯一入口。
     * </p>
     */
    private void applyEffect(String workspaceId, RetrospectiveFeedback feedback) {
        try {
            if (RetrospectiveFeedback.TARGET_TEMPLATE.equals(feedback.getTargetType())) {
                UUID templateId = parseUuid(feedback.getTargetId());
                if (templateId != null) {
                    templateEffectStatsService.applyTemplateEffect(workspaceId, feedback, null);
                }
            } else {
                templateEffectStatsService.applyKbDocumentEffect(workspaceId, feedback);
            }
        } catch (BusinessException ex) {
            // 效果统计写回失败不吞掉审批结论，但必须留下审计痕迹
            log.warn("Failed to apply retrospective effect (approval kept): feedback={} code={}",
                    feedback.getId(), ex.getCode());
            auditWriter.recordFailure("retrospective.feedback.apply", "RETROSPECTIVE_FEEDBACK",
                    feedback.getId().toString(), ex.getCode(), ex.getMessage(), null);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 建议生成
    // ══════════════════════════════════════════════════════════════════

    /**
     * 基于偏差对比行生成可执行建议。
     * <p>
     * <b>每条建议必须有非空 evidenceRefs</b>，且引用真实存在的
     * snapshot / comparison ID；无证据的建议直接丢弃。
     * </p>
     */
    private List<RetrospectiveResponse.RetrospectiveSuggestion> buildSuggestions(
            List<MetricComparison> comparisons, List<MetricSnapshot> snapshots, MetricForecast forecast) {

        List<RetrospectiveResponse.RetrospectiveSuggestion> suggestions = new ArrayList<>();
        Set<String> seenActions = new LinkedHashSet<>();

        // 按偏差率升序：偏差最严重的维度优先给建议
        List<MetricComparison> sorted = new ArrayList<>(comparisons);
        sorted.sort((a, b) -> {
            BigDecimalComparator cmp = new BigDecimalComparator();
            return cmp.compare(deviationMagnitude(a), deviationMagnitude(b));
        });

        for (MetricComparison row : sorted) {
            if (MetricComparison.HIT_HIT.equals(row.getHit())) {
                // 命中的维度不产生"改进建议"，只作为正向证据
                continue;
            }
            if (row.getSnapshotId() == null || row.getForecastId() == null) {
                continue;
            }
            // 证据必须真实存在
            if (snapshotMapper.selectByIdAndWorkspace(row.getSnapshotId(), row.getWorkspaceId()) == null
                    || comparisonMapper.selectByIdAndWorkspace(row.getId(), row.getWorkspaceId()) == null) {
                continue;
            }

            List<String> refs = List.of(row.getSnapshotId().toString(), row.getId().toString());
            String action = buildAction(row);
            if (action == null || !seenActions.add(action)) {
                continue;
            }

            suggestions.add(RetrospectiveResponse.RetrospectiveSuggestion.builder()
                    .type(suggestionType(row))
                    .action(action)
                    .expectedImpact(buildExpectedImpact(row))
                    .evidenceRefs(refs)
                    .confidence(confidenceOf(row))
                    .complianceFlags(List.of())
                    .build());

            if (suggestions.size() >= MIN_SUGGESTION_COUNT + 3) {
                break;
            }
        }

        // 对比行不足 3 条失败维度时，用命中维度做"巩固"建议补齐（同样带证据）
        if (suggestions.size() < MIN_SUGGESTION_COUNT) {
            for (MetricComparison row : comparisons) {
                if (suggestions.size() >= MIN_SUGGESTION_COUNT) {
                    break;
                }
                if (!MetricComparison.HIT_HIT.equals(row.getHit())) {
                    continue;
                }
                if (row.getSnapshotId() == null || row.getId() == null) {
                    continue;
                }
                if (snapshotMapper.selectByIdAndWorkspace(row.getSnapshotId(), row.getWorkspaceId()) == null
                        || comparisonMapper.selectByIdAndWorkspace(row.getId(), row.getWorkspaceId()) == null) {
                    continue;
                }
                String action = "延续该维度表现：保留" + dimensionLabel(row.getDimension())
                        + "的当前写法，沉淀为可复用模板";
                if (!seenActions.add(action)) {
                    continue;
                }
                suggestions.add(RetrospectiveResponse.RetrospectiveSuggestion.builder()
                        .type("TEMPLATE")
                        .action(action)
                        .expectedImpact("稳定已达标的" + dimensionLabel(row.getDimension()) + "表现")
                        .evidenceRefs(List.of(row.getSnapshotId().toString(), row.getId().toString()))
                        .confidence("HIGH")
                        .complianceFlags(List.of())
                        .build());
            }
        }

        return suggestions;
    }

    /**
     * 按维度类别给出可执行动作。
     */
    private String buildAction(MetricComparison row) {
        String dim = dimensionLabel(row.getDimension());
        String cat = row.getDimensionCategory();
        if (MetricComparison.CATEGORY_COVER.equals(cat)) {
            return "优化封面与曝光：重做首图/标题组合，把" + dim + "的预测缺口补上";
        }
        if (MetricComparison.CATEGORY_TITLE.equals(cat)) {
            return "改写标题：针对" + dim + "调整钩子与利益点表述，提升点击转化";
        }
        if (MetricComparison.CATEGORY_CONTENT_BODY.equals(cat)) {
            return "调整正文结构：拆解" + dim + "对应的段落，补足可收藏的信息点";
        }
        if (MetricComparison.CATEGORY_CTA.equals(cat)) {
            return "强化引导语：在" + dim + "环节补充明确行动号召，提升互动";
        }
        if (MetricComparison.CATEGORY_DISTRIBUTION.equals(cat)) {
            return "调整发布与分发：结合" + dim + "数据优化发布时间与人群定向";
        }
        return "复核" + dim + "的口径与内容呈现，排除数据偏差后再做内容调整";
    }

    /**
     * 预期影响描述（基于实际偏差率，不编造数字）。
     */
    private String buildExpectedImpact(MetricComparison row) {
        if (row.getDeviationPct() == null) {
            return "修正" + dimensionLabel(row.getDimension()) + "的预测偏差（偏差率不可计算，需先补齐基准）";
        }
        String pct = row.getDeviationPct().stripTrailingZeros().toPlainString();
        return "预计可回收" + dimensionLabel(row.getDimension()) + "的 " + pct + "% 偏差";
    }

    /**
     * 建议类型。
     */
    private String suggestionType(MetricComparison row) {
        String cat = row.getDimensionCategory();
        if (MetricComparison.CATEGORY_COVER.equals(cat)) {
            return "TOPIC";
        }
        if (MetricComparison.CATEGORY_TITLE.equals(cat)) {
            return "TITLE";
        }
        if (MetricComparison.CATEGORY_CTA.equals(cat) || MetricComparison.CATEGORY_CONTENT_BODY.equals(cat)) {
            return "BODY";
        }
        return "POST_TIME";
    }

    /**
     * 置信度：偏差越大、命中越明确，置信度越高。
     */
    private String confidenceOf(MetricComparison row) {
        if (MetricComparison.HIT_MISS.equals(row.getHit())) {
            return "HIGH";
        }
        if (MetricComparison.HIT_PARTIAL.equals(row.getHit())) {
            return "MEDIUM";
        }
        return "LOW";
    }

    // ══════════════════════════════════════════════════════════════════
    // 内部工具
    // ══════════════════════════════════════════════════════════════════

    /**
     * 失败维度的可能原因（基于确定性描述，不做无依据归因）。
     */
    private List<String> possibleReasons(MetricComparison row) {
        List<String> reasons = new ArrayList<>();
        String dim = dimensionLabel(row.getDimension());
        if (row.getActualValue() != null && row.getActualValue().signum() == 0) {
            reasons.add(dim + "实际为 0：内容可能未进入推荐池，或账号处于冷启动期");
        }
        if (row.getPredictedValue() != null && row.getPredictedValue().signum() == 0) {
            reasons.add(dim + "预测基准为 0：预测输入缺失，偏差率不可计算");
        }
        if (row.getDimensionCategory() != null) {
            reasons.add("偏差归类为" + row.getDimensionCategory() + "：建议先复核该环节的内容产出");
        }
        if (reasons.isEmpty()) {
            reasons.add(dim + "未达预测基准：建议对照同平台同期内容定位差距");
        }
        return reasons;
    }

    /**
     * 证据描述：指标 + 数值 + 来源（预测/实际 + 采集时间）。
     */
    private String buildEvidence(MetricComparison row) {
        StringBuilder sb = new StringBuilder();
        sb.append(dimensionLabel(row.getDimension()));
        sb.append("：预测 ").append(row.getPredictedValue() == null ? "?" : row.getPredictedValue().stripTrailingZeros().toPlainString());
        sb.append("，实际 ").append(row.getActualValue() == null ? "?" : row.getActualValue().stripTrailingZeros().toPlainString());
        if (row.getDeviationPct() != null) {
            sb.append("，偏差率 ").append(row.getDeviationPct().stripTrailingZeros().toPlainString()).append("%");
        }
        sb.append("（判定 ").append(row.getHit()).append("）");
        if (row.getReason() != null && !row.getReason().isBlank()) {
            sb.append("；").append(row.getReason());
        }
        return sb.toString();
    }

    /**
     * 报告摘要。
     */
    private String buildSummary(String status, Map<String, Long> predicted, Map<String, Long> actual,
                                List<MetricComparison> comparisons,
                                List<RetrospectiveResponse.RetrospectiveSuggestion> suggestions,
                                List<String> missing) {
        if (!missing.isEmpty()) {
            return "数据不足（缺少 " + String.join("、", missing) + "），无法给出可追溯的复盘结论。";
        }
        long hit = comparisons.stream().filter(c -> MetricComparison.HIT_HIT.equals(c.getHit())).count();
        long miss = comparisons.stream().filter(c -> MetricComparison.HIT_MISS.equals(c.getHit())).count();
        return "状态 " + status + "：共对比 " + comparisons.size() + " 个维度，命中 " + hit
                + " 个、未达预测 " + miss + " 个，生成 " + suggestions.size() + " 条带证据建议。";
    }

    /**
     * 解析维度键。
     */
    private String resolveScopeKey(RetrospectiveCreateRequest request, String scopeType,
                                   UUID assetId, String platformCode) {
        if (request.getScopeKey() != null && !request.getScopeKey().isBlank()) {
            return request.getScopeKey().trim();
        }
        return switch (scopeType) {
            case RetrospectiveReport.SCOPE_ASSET -> assetId == null ? "UNKNOWN" : assetId.toString();
            case RetrospectiveReport.SCOPE_PLATFORM -> platformCode == null ? "UNKNOWN" : platformCode;
            case RetrospectiveReport.SCOPE_ACCOUNT -> blankToNull(request.getAccountId()) == null
                    ? "UNKNOWN" : request.getAccountId().trim();
            case RetrospectiveReport.SCOPE_CATEGORY -> blankToNull(request.getCategoryCode()) == null
                    ? "UNKNOWN" : request.getCategoryCode().trim();
            case RetrospectiveReport.SCOPE_CAMPAIGN -> blankToNull(request.getCampaignId()) == null
                    ? "UNKNOWN" : request.getCampaignId().trim();
            default -> "PERIOD";
        };
    }

    /**
     * 预测侧 / 实际侧各自打包（带来源时间），<b>分字段</b>不混。
     */
    private Map<String, Object> toSideMap(Map<String, Long> metrics, OffsetDateTime sourceTime) {
        Map<String, Object> side = new LinkedHashMap<>();
        side.put("metrics", metrics);
        side.put("sourceTime", sourceTime == null ? null : sourceTime.toString());
        return side;
    }

    /**
     * 聚合多条快照：同维度取最新一条非空值。
     */
    private Map<String, Long> aggregateLatest(List<MetricSnapshot> snapshots) {
        Map<String, Long> aggregated = new LinkedHashMap<>();
        if (snapshots.isEmpty()) {
            return aggregated;
        }
        MetricSnapshot newest = snapshots.get(0);
        Map<String, Long> values = readCoreMetrics(newest.getMetrics());
        if (!values.isEmpty()) {
            aggregated.putAll(values);
            return aggregated;
        }
        // 最新的快照没有指标时，逐条回溯
        for (MetricSnapshot snapshot : snapshots) {
            Map<String, Long> candidate = readCoreMetrics(snapshot.getMetrics());
            if (!candidate.isEmpty()) {
                aggregated.putAll(candidate);
                return aggregated;
            }
        }
        return aggregated;
    }

    private Map<String, Long> readCoreMetrics(String json) {
        Map<String, Long> core = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return core;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            for (String key : MetricKeys.CORE_KEYS) {
                long value = toLong(parsed.get(key));
                if (value != 0L) {
                    core.put(key, value);
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to parse metrics JSON: {}", ex.getMessage());
        }
        return core;
    }

    /**
     * 偏差幅度（用于排序）；无偏差率时按 0 处理。
     */
    private java.math.BigDecimal deviationMagnitude(MetricComparison row) {
        if (row.getDeviationPct() == null) {
            return java.math.BigDecimal.ZERO;
        }
        return row.getDeviationPct().abs();
    }

    /**
     * 维度中文/英文标签（后端只做中性标签，前端 i18n 覆盖展示）。
     */
    private String dimensionLabel(String dimension) {
        if (dimension == null) {
            return "指标";
        }
        return switch (dimension) {
            case MetricKeys.IMPRESSIONS -> "曝光";
            case MetricKeys.PLAYS -> "播放";
            case MetricKeys.CLICKS -> "点击";
            case MetricKeys.LIKES -> "点赞";
            case MetricKeys.COLLECTS -> "收藏";
            case MetricKeys.COMMENTS -> "评论";
            case MetricKeys.SHARES -> "分享";
            case MetricKeys.FOLLOWS -> "关注";
            case MetricKeys.DM_INQUIRIES -> "私信咨询";
            case MetricKeys.CONVERSIONS -> "转化";
            default -> dimension;
        };
    }

    private RetrospectiveCreateResponse toCreateResponse(RetrospectiveReport report) {
        return RetrospectiveCreateResponse.builder()
                .reportId(report.getId())
                .taskId(report.getId())
                .status(report.getStatus())
                .degraded(report.getDegraded())
                .degradedReason(report.getDegradedReason())
                .suggestionCount(countSuggestions(report.getSuggestions()))
                .snapshotCount(null)
                .build();
    }

    private RetrospectiveSummaryResponse toSummary(RetrospectiveReport report) {
        return RetrospectiveSummaryResponse.builder()
                .id(report.getId())
                .scopeType(report.getScopeType())
                .scopeKey(report.getScopeKey())
                .suggestionCount(countSuggestions(report.getSuggestions()))
                .status(report.getStatus())
                .degraded(report.getDegraded())
                .generatedAt(report.getGeneratedAt())
                .build();
    }

    private RetrospectiveResponse toResponse(RetrospectiveReport report) {
        return RetrospectiveResponse.builder()
                .id(report.getId())
                .scopeType(report.getScopeType())
                .scopeKey(report.getScopeKey())
                .assetId(report.getAssetId())
                .accountId(report.getAccountId())
                .platformCode(report.getPlatformCode())
                .categoryCode(report.getCategoryCode())
                .campaignId(report.getCampaignId())
                .periodStart(report.getPeriodStart())
                .periodEnd(report.getPeriodEnd())
                .summary(report.getSummary())
                .predictions(readMap(report.getPredictions()))
                .actuals(readMap(report.getActuals()))
                .hitDimensions(readDimensionEvidence(report.getHitDimensions()))
                .missedDimensions(readDimensionEvidence(report.getMissedDimensions()))
                .suggestions(readSuggestions(report.getSuggestions()))
                .missingDimensions(readStringList(report.getMissingDimensions()))
                .status(report.getStatus())
                .degraded(report.getDegraded())
                .degradedReason(report.getDegradedReason())
                .modelName(report.getModelName())
                .promptVersion(report.getPromptVersion())
                .kbVersion(report.getKbVersion())
                .generatedAt(report.getGeneratedAt())
                .build();
    }

    private RetrospectiveFeedbackResponse toFeedbackResponse(RetrospectiveFeedback feedback) {
        return RetrospectiveFeedbackResponse.builder()
                .id(feedback.getId())
                .reportId(feedback.getReportId())
                .targetType(feedback.getTargetType())
                .targetId(feedback.getTargetId())
                .proposedContent(feedback.getProposedContent())
                .proposedStats(readMap(feedback.getProposedStats()))
                .status(feedback.getStatus())
                .reviewedBy(feedback.getReviewedBy())
                .reviewedAt(feedback.getReviewedAt())
                .reviewComment(feedback.getReviewComment())
                .appliedAt(feedback.getAppliedAt())
                .createdAt(feedback.getCreatedAt())
                .build();
    }

    private int countSuggestions(String json) {
        return readSuggestions(json).size();
    }

    @SuppressWarnings("unchecked")
    private List<RetrospectiveResponse.RetrospectiveSuggestion> readSuggestions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<RetrospectiveResponse.RetrospectiveSuggestion> parsed = objectMapper.readValue(json,
                    new TypeReference<List<RetrospectiveResponse.RetrospectiveSuggestion>>() {
                    });
            return parsed == null ? List.of() : parsed;
        } catch (Exception ex) {
            log.warn("Failed to parse suggestions JSON: {}", ex.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<RetrospectiveResponse.DimensionEvidence> readDimensionEvidence(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<RetrospectiveResponse.DimensionEvidence> parsed = objectMapper.readValue(json,
                    new TypeReference<List<RetrospectiveResponse.DimensionEvidence>>() {
                    });
            return parsed == null ? List.of() : parsed;
        } catch (Exception ex) {
            log.warn("Failed to parse dimension evidence JSON: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return parsed == null ? List.of() : parsed;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            return parsed == null ? Map.of() : parsed;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("Failed to serialize retrospective JSON: {}", ex.getMessage());
            return "{}";
        }
    }

    private void requireWorkspace(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new BusinessException(CODE_RETROSPECTIVE_INVALID,
                    "RETROSPECTIVE_INVALID: workspace_id_required");
        }
    }

    private String normalizeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.length() > 128 ? trimmed.substring(0, 128) : trimmed;
    }

    private String upper(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    /**
     * 简单 BigDecimal 比较器（避免额外依赖）。
     */
    private static final class BigDecimalComparator
            implements java.util.Comparator<java.math.BigDecimal> {
        @Override
        public int compare(java.math.BigDecimal a, java.math.BigDecimal b) {
            return a.compareTo(b);
        }
    }
}
