package com.shortvideoscripagent.xhsagentyunying.template;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.Template;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.TemplateApplyLog;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.TemplateVersion;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.TemplateApplyLogMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.TemplateMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.TemplateVersionMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.analysis.PaginatedResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.template.*;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceGuards;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceRole;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 模板库 Service（P0-4）。
 * <p>
 * 核心能力：三级范围（OFFICIAL/WORKSPACE/PERSONAL）、变量替换、版本管理、复制、统计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

    private static final int CODE_NOT_FOUND = 40410;
    private static final int CODE_FORBIDDEN = 40301;
    private static final int CODE_BAD_REQUEST = 40010;
    private static final int CODE_CONFLICT = 40910;

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    private final TemplateMapper templateMapper;
    private final TemplateVersionMapper versionMapper;
    private final TemplateApplyLogMapper applyLogMapper;
    private final WorkspaceService workspaceService;
    private final ObjectMapper objectMapper;

    // ── 查询 ──────────────────────────────────────────────────────────────

    /**
     * 分页查询模板列表。
     * <ul>
     *   <li>scope=OFFICIAL：所有已登录用户可见</li>
     *   <li>scope=WORKSPACE：同 workspace 成员可见</li>
     *   <li>scope=PERSONAL：仅 owner 可见</li>
     * </ul>
     */
    public PaginatedResponse<TemplateResponse> list(
            Long userId,
            String scope,
            String category,
            int page,
            int size
    ) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        UUID wsUuid = parseUuid(workspaceId);

        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        long offset = (safePage - 1L) * safeSize;

        // scope 参数过滤
        String effectiveScope = normalizeScope(scope);

        Page<Template> p = new Page<>(safePage, safeSize);
        IPage<Template> result;

        if ("OFFICIAL".equals(effectiveScope)) {
            result = templateMapper.selectPageByScope(
                    p, null, null, category, offset, safeSize);
        } else if ("WORKSPACE".equals(effectiveScope)) {
            result = templateMapper.selectPageByScope(
                    p, wsUuid, null, category, offset, safeSize);
        } else if ("PERSONAL".equals(effectiveScope)) {
            result = templateMapper.selectPageByScope(
                    p, null, userId, category, offset, safeSize);
        } else {
            // 无 scope 参数：返回当前用户有权限看到的全部模板
            result = templateMapper.selectPageByScope(
                    p, wsUuid, userId, category, offset, safeSize);
        }

        List<TemplateResponse> items = result.getRecords().stream()
                .map(this::toResponse)
                .toList();

        return new PaginatedResponse<>(items, result.getTotal(), safePage, safeSize);
    }

    /**
     * 获取单个模板详情。
     */
    public TemplateResponse getById(Long userId, UUID templateId) {
        Template t = templateMapper.selectById(templateId);
        if (t == null) {
            throw new BusinessException(CODE_NOT_FOUND, "template_not_found");
        }
        requireViewable(userId, t);
        return toResponse(t);
    }

    // ── 创建 ──────────────────────────────────────────────────────────────

    /**
     * 创建模板（仅 WORKSPACE / PERSONAL 可创建，OFFICIAL 不可通过 API 创建）。
     */
    @Transactional
    public TemplateResponse create(Long creatorId, TemplateCreateRequest req) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        UUID wsUuid = parseUuid(workspaceId);

        String scope = req.getScope();
        if (Template.SCOPE_OFFICIAL.equals(scope)) {
            throw new BusinessException(CODE_FORBIDDEN, "cannot_create_official_template");
        }

        // 权限校验
        requireEditorRole(creatorId, workspaceId);

        // 字段校验
        if (Template.SCOPE_WORKSPACE.equals(scope) && req.getWorkspaceId() == null) {
            throw new BusinessException(CODE_BAD_REQUEST, "workspace_id_required_for_workspace_scope");
        }

        OffsetDateTime now = OffsetDateTime.now();

        Template t = new Template();
        t.setId(UUID.randomUUID());
        t.setScope(scope);
        t.setWorkspaceId(Template.SCOPE_WORKSPACE.equals(scope) ? wsUuid : null);
        t.setOwnerUserId(Template.SCOPE_PERSONAL.equals(scope) ? creatorId : null);
        t.setCategory(req.getCategory());
        t.setScenario(req.getScenario());
        t.setPersona(req.getPersona());
        t.setTitle(req.getTitle());
        t.setBodyTemplate(req.getBodyTemplate());
        t.setVariables(serializeVariables(deriveVariables(req)));
        t.setDefaultGoal(req.getDefaultGoal());
        t.setDefaultTone(req.getDefaultTone());
        t.setVersion(1);
        t.setStatus(Template.STATUS_ACTIVE);
        t.setUseCount(0);
        t.setCreatedAt(now);
        t.setUpdatedAt(now);

        templateMapper.insert(t);

        // 同步写入 v1 版本快照
        saveVersionSnapshot(t, creatorId);

        return toResponse(t);
    }

    // ── 更新 ──────────────────────────────────────────────────────────────

    /**
     * 更新模板（OFFICIAL 不可修改）。
     */
    @Transactional
    public TemplateResponse update(Long editorId, UUID templateId, TemplateUpdateRequest req) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        Template t = requireEditable(editorId, templateId, workspaceId);

        boolean dirty = false;
        if (req.getTitle() != null) {
            t.setTitle(req.getTitle().trim());
            dirty = true;
        }
        if (req.getBodyTemplate() != null) {
            t.setBodyTemplate(req.getBodyTemplate());
            // body 变了，自动从新 body 重新提取变量
            List<String> vars = extractVariablesFromTemplate(req.getBodyTemplate());
            if (req.getVariables() == null) {
                t.setVariables(serializeVariables(vars));
            } else {
                t.setVariables(serializeVariables(req.getVariables()));
            }
            dirty = true;
        }
        if (req.getVariables() != null) {
            t.setVariables(serializeVariables(req.getVariables()));
            dirty = true;
        }
        if (req.getCategory() != null) {
            t.setCategory(req.getCategory());
            dirty = true;
        }
        if (req.getScenario() != null) {
            t.setScenario(req.getScenario());
            dirty = true;
        }
        if (req.getPersona() != null) {
            t.setPersona(req.getPersona());
            dirty = true;
        }
        if (req.getDefaultGoal() != null) {
            t.setDefaultGoal(req.getDefaultGoal());
            dirty = true;
        }
        if (req.getDefaultTone() != null) {
            t.setDefaultTone(req.getDefaultTone());
            dirty = true;
        }

        if (!dirty) {
            return toResponse(t);
        }

        t.setVersion(t.getVersion() + 1);
        t.setUpdatedAt(OffsetDateTime.now());
        templateMapper.updateById(t);

        // 写新版本快照
        saveVersionSnapshot(t, editorId);

        return toResponse(t);
    }

    // ── 发布（仅 WORKSPACE/PERSONAL）────────────────────────────────────────

    /**
     * 发布模板新版本（OFFICIAL 不可发布，WORKSPACE/PERSONAL 由编辑器发布）。
     */
    @Transactional
    public TemplateResponse publish(Long publisherId, UUID templateId) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        Template t = requireEditable(publisherId, templateId, workspaceId);
        t.setVersion(t.getVersion() + 1);
        t.setUpdatedAt(OffsetDateTime.now());
        templateMapper.updateById(t);
        saveVersionSnapshot(t, publisherId);
        return toResponse(t);
    }

    // ── 归档 ──────────────────────────────────────────────────────────────

    /**
     * 归档模板（OFFICIAL 不可归档）。
     */
    @Transactional
    public void archive(Long userId, UUID templateId) {
        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        Template t = requireEditable(userId, templateId, workspaceId);
        t.setStatus(Template.STATUS_ARCHIVED);
        t.setUpdatedAt(OffsetDateTime.now());
        templateMapper.updateById(t);
    }

    // ── 复制 ──────────────────────────────────────────────────────────────

    /**
     * 复制模板到目标工作区（OFFICIAL → WORKSPACE 复制）。
     */
    @Transactional
    public TemplateResponse duplicate(Long userId, UUID templateId, String targetWorkspaceId) {
        Template source = templateMapper.selectById(templateId);
        if (source == null) {
            throw new BusinessException(CODE_NOT_FOUND, "template_not_found");
        }

        String currentWorkspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        UUID targetWsUuid = parseUuid(targetWorkspaceId);

        // OFFICIAL 模板：可复制到当前 workspace
        // WORKSPACE 模板：同 workspace 可复制
        // PERSONAL 模板：仅 owner 可复制
        if (Template.SCOPE_OFFICIAL.equals(source.getScope())) {
            // 无额外限制
        } else if (Template.SCOPE_WORKSPACE.equals(source.getScope())) {
            if (!currentWorkspaceId.equalsIgnoreCase(targetWorkspaceId)) {
                requireEditorRole(userId, targetWorkspaceId);
            }
        } else {
            if (!source.getOwnerUserId().equals(userId)) {
                throw new BusinessException(CODE_FORBIDDEN, "cannot_duplicate_personal_template");
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        Template copy = new Template();
        copy.setId(UUID.randomUUID());
        copy.setScope(Template.SCOPE_WORKSPACE);
        copy.setWorkspaceId(targetWsUuid);
        copy.setOwnerUserId(null);
        copy.setCategory(source.getCategory());
        copy.setScenario(source.getScenario());
        copy.setPersona(source.getPersona());
        copy.setTitle("[复制] " + source.getTitle());
        copy.setBodyTemplate(source.getBodyTemplate());
        copy.setVariables(source.getVariables());
        copy.setDefaultGoal(source.getDefaultGoal());
        copy.setDefaultTone(source.getDefaultTone());
        copy.setVersion(1);
        copy.setStatus(Template.STATUS_ACTIVE);
        copy.setUseCount(0);
        copy.setCreatedAt(now);
        copy.setUpdatedAt(now);

        templateMapper.insert(copy);
        saveVersionSnapshot(copy, userId);

        return toResponse(copy);
    }

    // ── 渲染（变量替换）────────────────────────────────────────────────────

    /**
     * 渲染模板：将 {{variable}} 占位符替换为实际值。
     * <p>
     * 缺失的必填变量会报错。
     */
    public TemplateRenderResponse render(UUID templateId, Map<String, String> variables) {
        Template t = templateMapper.selectById(templateId);
        if (t == null) {
            throw new BusinessException(CODE_NOT_FOUND, "template_not_found");
        }
        if (!Template.STATUS_ACTIVE.equals(t.getStatus())) {
            throw new BusinessException(CODE_CONFLICT, "template_not_active");
        }

        List<String> requiredVars = parseVariables(t.getVariables());
        List<String> missing = requiredVars.stream()
                .filter(v -> !variables.containsKey(v) || variables.get(v) == null || variables.get(v).isBlank())
                .toList();

        if (!missing.isEmpty()) {
            throw new BusinessException(CODE_BAD_REQUEST,
                    "missing_required_variables: " + String.join(", ", missing));
        }

        String rendered = t.getBodyTemplate();
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() != null ? entry.getValue() : "";
            // 全局替换所有 {{key}} → value
            rendered = rendered.replace("{{" + key + "}}", value);
        }

        return TemplateRenderResponse.builder()
                .templateId(templateId.toString())
                .body(rendered)
                .build();
    }

    // ── 应用（模板→资产）──────────────────────────────────────────────────

    /**
     * 将模板应用到资产：渲染 + 写 apply_log + use_count++。
     * <p>
     * 返回渲染后的正文（供调用方写入 content_asset.body）。
     */
    @Transactional
    public TemplateApplyResult apply(
            Long userId,
            UUID templateId,
            UUID assetId,
            Map<String, String> variables
    ) {
        Template t = templateMapper.selectById(templateId);
        if (t == null) {
            throw new BusinessException(CODE_NOT_FOUND, "template_not_found");
        }
        if (!Template.STATUS_ACTIVE.equals(t.getStatus())) {
            throw new BusinessException(CODE_CONFLICT, "template_not_active");
        }

        String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
        UUID wsUuid = parseUuid(workspaceId);

        // 渲染
        TemplateRenderResponse rendered = render(templateId, variables);

        // 写 apply log
        TemplateApplyLog log = new TemplateApplyLog();
        log.setId(UUID.randomUUID());
        log.setTemplateId(templateId);
        log.setAssetId(assetId);
        log.setWorkspaceId(wsUuid);
        log.setUserId(userId);
        log.setCreatedAt(OffsetDateTime.now());
        applyLogMapper.insert(log);

        // use_count++
        templateMapper.incrementUseCount(templateId);

        // 重新查询最新 use_count
        Template updated = templateMapper.selectById(templateId);

        return new TemplateApplyResult(rendered.getBody(), updated.getUseCount());
    }

    // ── 统计 ──────────────────────────────────────────────────────────────

    /**
     * 获取模板引用统计（最近 30 天，按日聚合）。
     */
    public TemplateApplyStatsResponse getApplyStats(UUID templateId) {
        Template t = templateMapper.selectById(templateId);
        if (t == null) {
            throw new BusinessException(CODE_NOT_FOUND, "template_not_found");
        }

        OffsetDateTime from = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30);
        List<TemplateApplyLogMapper.DailyStat> rawStats = applyLogMapper.selectDailyStats(templateId, from);

        List<TemplateApplyStatsResponse.DailyStat> dailyStats = rawStats.stream()
                .map(s -> TemplateApplyStatsResponse.DailyStat.builder()
                        .day(s.getDay())
                        .count(s.getCount())
                        .build())
                .toList();

        long totalCount = applyLogMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TemplateApplyLog>()
                        .eq(TemplateApplyLog::getTemplateId, templateId));

        return TemplateApplyStatsResponse.builder()
                .templateId(templateId.toString())
                .totalCount(totalCount)
                .dailyStats(dailyStats)
                .build();
    }

    // ── 权限校验辅助 ──────────────────────────────────────────────────────

    /**
     * 要求用户可查看该模板。
     */
    private void requireViewable(Long userId, Template t) {
        if (Template.SCOPE_OFFICIAL.equals(t.getScope())) {
            return; // 所有人可见
        }
        if (Template.SCOPE_WORKSPACE.equals(t.getScope())) {
            String workspaceId = WorkspaceGuards.requireCurrentWorkspaceId();
            if (t.getWorkspaceId() != null && t.getWorkspaceId().toString().equalsIgnoreCase(workspaceId)) {
                return;
            }
        }
        if (Template.SCOPE_PERSONAL.equals(t.getScope())) {
            if (t.getOwnerUserId() != null && t.getOwnerUserId().equals(userId)) {
                return;
            }
        }
        throw new BusinessException(CODE_NOT_FOUND, "template_not_found");
    }

    /**
     * 要求用户可编辑该模板（OFFICIAL 不可编辑）。
     */
    private Template requireEditable(Long userId, UUID templateId, String workspaceId) {
        Template t = templateMapper.selectById(templateId);
        if (t == null) {
            throw new BusinessException(CODE_NOT_FOUND, "template_not_found");
        }
        if (Template.SCOPE_OFFICIAL.equals(t.getScope())) {
            throw new BusinessException(CODE_FORBIDDEN, "official_template_not_editable");
        }
        requireViewable(userId, t);
        requireEditorRole(userId, workspaceId);
        return t;
    }

    /**
     * 要求用户在当前 workspace 具有 EDITOR 及以上权限。
     */
    private void requireEditorRole(Long userId, String workspaceId) {
        WorkspaceRole role = workspaceService.findRole(userId, workspaceId);
        if (role == null) {
            throw new BusinessException(CODE_FORBIDDEN, "workspace_access_denied");
        }
        if (!role.atLeast(WorkspaceRole.EDITOR)) {
            throw new BusinessException(CODE_FORBIDDEN, "editor_role_required");
        }
    }

    // ── 版本快照 ─────────────────────────────────────────────────────────

    private void saveVersionSnapshot(Template t, Long publishedBy) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("title", t.getTitle());
            snapshot.put("bodyTemplate", t.getBodyTemplate());
            snapshot.put("variables", parseVariables(t.getVariables()));
            snapshot.put("defaultGoal", t.getDefaultGoal());
            snapshot.put("defaultTone", t.getDefaultTone());
            snapshot.put("category", t.getCategory());
            snapshot.put("scenario", t.getScenario());
            snapshot.put("persona", t.getPersona());

            String snapshotJson = objectMapper.writeValueAsString(snapshot);

            TemplateVersion v = new TemplateVersion();
            v.setId(UUID.randomUUID());
            v.setTemplateId(t.getId());
            v.setVersionNo(t.getVersion());
            v.setSnapshot(snapshotJson);
            v.setPublishedAt(OffsetDateTime.now());
            v.setPublishedBy(publishedBy);
            versionMapper.insert(v);
        } catch (JsonProcessingException e) {
            throw new BusinessException(CODE_BAD_REQUEST, "snapshot_serialization_failed");
        }
    }

    // ── 工具方法 ──────────────────────────────────────────────────────────

    private String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return null;
        }
        String upper = scope.toUpperCase().trim();
        if (upper.equals("OFFICIAL") || upper.equals("WORKSPACE") || upper.equals("PERSONAL")) {
            return upper;
        }
        return null;
    }

    private List<String> deriveVariables(TemplateCreateRequest req) {
        if (req.getVariables() != null && !req.getVariables().isEmpty()) {
            return req.getVariables();
        }
        return extractVariablesFromTemplate(req.getBodyTemplate());
    }

    private List<String> extractVariablesFromTemplate(String bodyTemplate) {
        if (bodyTemplate == null || bodyTemplate.isBlank()) {
            return List.of();
        }
        Set<String> vars = new LinkedHashSet<>();
        Matcher m = VAR_PATTERN.matcher(bodyTemplate);
        while (m.find()) {
            vars.add(m.group(1));
        }
        return new ArrayList<>(vars);
    }

    private List<String> parseVariables(String variables) {
        if (variables == null || variables.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(variables, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse variables JSON: {}", variables);
            return List.of();
        }
    }

    private String serializeVariables(List<String> vars) {
        try {
            return objectMapper.writeValueAsString(vars != null ? vars : List.of());
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private TemplateResponse toResponse(Template t) {
        return TemplateResponse.builder()
                .id(t.getId())
                .scope(t.getScope())
                .workspaceId(t.getWorkspaceId())
                .ownerUserId(t.getOwnerUserId())
                .category(t.getCategory())
                .scenario(t.getScenario())
                .persona(t.getPersona())
                .title(t.getTitle())
                .bodyTemplate(t.getBodyTemplate())
                .variables(parseVariables(t.getVariables()))
                .defaultGoal(t.getDefaultGoal())
                .defaultTone(t.getDefaultTone())
                .version(t.getVersion())
                .status(t.getStatus())
                .useCount(t.getUseCount())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception e) {
            throw new BusinessException(CODE_BAD_REQUEST, "invalid_uuid");
        }
    }

    // ── 内部结果类 ────────────────────────────────────────────────────────

    /** apply() 的内部返回结果 */
    public record TemplateApplyResult(String body, Integer updatedUseCount) {}
}
