package com.shortvideoscripagent.xhsagentyunying.template;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.Template;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.TemplateVersion;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.TemplateMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.TemplateVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 官方模板种子数据初始化。
 * <p>
 * 从 {@code resources/data/templates/seed.json} 读取官方种子模板写入数据库。
 * <p>
 * <b>幂等策略（V59 起）</b>：以 {@code code} 字段（app.template.code，scope=OFFICIAL 时唯一）
 * 作为幂等键，而不是「OFFICIAL 模板非空就跳过」。
 * <ul>
 *   <li>code 不存在 —— 插入新模板 + 写 v1 快照</li>
 *   <li>code 已存在 —— 原地更新品类 / 标题 / 正文 / 变量，并追加一条新版本快照</li>
 * </ul>
 * 这样产品定位调整（如从求职垂类迁移到全平台全品类）后，
 * 存量库重启即可自动收敛到新的种子内容，不需要人工清理数据库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateSeedRunner {

    private final TemplateMapper templateMapper;
    private final TemplateVersionMapper versionMapper;
    private final ObjectMapper objectMapper;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onApplicationReady() {
        try {
            List<Map<String, Object>> seeds = loadSeedData();
            int inserted = 0;
            int updated = 0;
            int unchanged = 0;

            for (Map<String, Object> seed : seeds) {
                String code = (String) seed.get("code");
                if (code == null || code.isBlank()) {
                    log.warn("[TemplateSeedRunner] seed entry without code, skipped: {}", seed.get("title"));
                    continue;
                }

                Template existing = findByCode(code);
                if (existing == null) {
                    insertSeed(code, seed);
                    inserted++;
                } else if (applySeed(existing, seed)) {
                    updateSeed(existing, seed);
                    updated++;
                } else {
                    unchanged++;
                }
            }

            log.info("[TemplateSeedRunner] Seed sync done: inserted={}, updated={}, unchanged={}",
                    inserted, updated, unchanged);
        } catch (Exception e) {
            log.error("[TemplateSeedRunner] Failed to sync official templates", e);
        }
    }

    // ── 写入 ────────────────────────────────────────────────────────────────

    private void insertSeed(String code, Map<String, Object> seed) throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        Template t = new Template();
        t.setId(UUID.randomUUID());
        t.setScope(Template.SCOPE_OFFICIAL);
        t.setWorkspaceId(null);
        t.setOwnerUserId(null);
        t.setCode(code);
        t.setCategory((String) seed.get("category"));
        t.setScenario((String) seed.get("scenario"));
        t.setPersona((String) seed.get("persona"));
        t.setTitle((String) seed.get("title"));
        t.setBodyTemplate((String) seed.get("bodyTemplate"));
        t.setVariables(toVariablesJson(seed.get("variables")));
        t.setDefaultGoal((String) seed.getOrDefault("defaultGoal", "engagement"));
        t.setDefaultTone((String) seed.getOrDefault("defaultTone", "friendly"));
        t.setVersion(1);
        t.setStatus(Template.STATUS_ACTIVE);
        t.setUseCount(0);
        t.setCreatedAt(now);
        t.setUpdatedAt(now);

        templateMapper.insert(t);
        saveVersionSnapshot(t, 1);

        log.info("[TemplateSeedRunner] Seeded template: {} ({} / {})", code, t.getCategory(), t.getTitle());
    }

    /**
     * 把种子内容套用到已有模板上。
     *
     * @return true 表示有字段发生变化（需要落库 + 记新版本）
     */
    private boolean applySeed(Template t, Map<String, Object> seed) {
        boolean dirty = false;

        if (!equalText(t.getCategory(), (String) seed.get("category"))) {
            t.setCategory((String) seed.get("category"));
            dirty = true;
        }
        if (!equalText(t.getTitle(), (String) seed.get("title"))) {
            t.setTitle((String) seed.get("title"));
            dirty = true;
        }
        if (!equalText(t.getBodyTemplate(), (String) seed.get("bodyTemplate"))) {
            t.setBodyTemplate((String) seed.get("bodyTemplate"));
            dirty = true;
        }
        if (!equalText(t.getScenario(), (String) seed.get("scenario"))) {
            t.setScenario((String) seed.get("scenario"));
            dirty = true;
        }
        if (!equalText(t.getPersona(), (String) seed.get("persona"))) {
            t.setPersona((String) seed.get("persona"));
            dirty = true;
        }
        String seedVariables = toVariablesJson(seed.get("variables"));
        if (!equalText(t.getVariables(), seedVariables)) {
            t.setVariables(seedVariables);
            dirty = true;
        }
        if (!equalText(t.getDefaultGoal(), (String) seed.getOrDefault("defaultGoal", "engagement"))) {
            t.setDefaultGoal((String) seed.getOrDefault("defaultGoal", "engagement"));
            dirty = true;
        }
        if (!equalText(t.getDefaultTone(), (String) seed.getOrDefault("defaultTone", "friendly"))) {
            t.setDefaultTone((String) seed.getOrDefault("defaultTone", "friendly"));
            dirty = true;
        }
        // 种子模板恒为 active（被用户归档的官方模板重新拉回 active，避免新品类下拉为空）
        if (!Template.STATUS_ACTIVE.equals(t.getStatus())) {
            t.setStatus(Template.STATUS_ACTIVE);
            dirty = true;
        }
        return dirty;
    }

    private void updateSeed(Template t, Map<String, Object> seed) throws Exception {
        t.setUpdatedAt(OffsetDateTime.now());
        t.setVersion(t.getVersion() + 1);
        templateMapper.updateById(t);
        saveVersionSnapshot(t, t.getVersion());

        log.info("[TemplateSeedRunner] Updated template: {} ({} / {})", t.getCode(), t.getCategory(), t.getTitle());
    }

    // ── 查询 ────────────────────────────────────────────────────────────────

    private Template findByCode(String code) {
        List<Template> list = templateMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Template>()
                        .eq(Template::getScope, Template.SCOPE_OFFICIAL)
                        .eq(Template::getCode, code)
                        .last("LIMIT 1")
        );
        return list.isEmpty() ? null : list.get(0);
    }

    // ── 工具 ────────────────────────────────────────────────────────────────

    private static boolean equalText(String a, String b) {
        String x = a == null ? "" : a;
        String y = b == null ? "" : b;
        return x.equals(y);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadSeedData() throws Exception {
        ClassPathResource resource = new ClassPathResource("data/templates/seed.json");
        try (InputStream is = resource.getInputStream()) {
            return objectMapper.readValue(is, new TypeReference<>() {
            });
        }
    }

    /**
     * 把种子里的变量列表序列化为 JSON 数组字符串。
     * <p>
     * <b>不抛受检异常</b>：本方法在 applySeed（比较逻辑）里被调用，
     * 而 applySeed 不声明 throws；序列化失败降级为空数组而不是中断启动。
     */
    private String toVariablesJson(Object vars) {
        if (vars instanceof List<?>) {
            try {
                return objectMapper.writeValueAsString(vars);
            } catch (Exception e) {
                log.warn("[TemplateSeedRunner] Failed to serialize seed variables, fallback to []", e);
                return "[]";
            }
        }
        return "[]";
    }

    private void saveVersionSnapshot(Template t, int versionNo) throws Exception {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", t.getCode() != null ? t.getCode() : "");
        snapshot.put("title", t.getTitle());
        snapshot.put("bodyTemplate", t.getBodyTemplate());
        snapshot.put("variables", objectMapper.readValue(t.getVariables(), List.class));
        snapshot.put("defaultGoal", t.getDefaultGoal() != null ? t.getDefaultGoal() : "");
        snapshot.put("defaultTone", t.getDefaultTone() != null ? t.getDefaultTone() : "");
        snapshot.put("category", t.getCategory() != null ? t.getCategory() : "");
        snapshot.put("scenario", t.getScenario() != null ? t.getScenario() : "");
        snapshot.put("persona", t.getPersona() != null ? t.getPersona() : "");

        TemplateVersion v = new TemplateVersion();
        v.setId(UUID.randomUUID());
        v.setTemplateId(t.getId());
        v.setVersionNo(versionNo);
        v.setSnapshot(objectMapper.writeValueAsString(snapshot));
        v.setPublishedAt(t.getUpdatedAt() != null ? t.getUpdatedAt() : OffsetDateTime.now());
        v.setPublishedBy(null);  // 系统自动同步，无发布人
        versionMapper.insert(v);
    }
}
