package com.shortvideoscripagent.xhsagentyunying.template;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.Template;
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
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 官方模板种子数据初始化（P0-4）。
 * <p>
 * 在应用启动完成后，检查 OFFICIAL 模板是否为空，如果为空则从
 * {@code resources/data/templates/seed.json} 读取 8 个官方种子模板并插入数据库。
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
        long officialCount = templateMapper.countOfficialActive();
        if (officialCount > 0) {
            log.info("[TemplateSeedRunner] OFFICIAL templates already exist (count={}), skip seeding.", officialCount);
            return;
        }

        log.info("[TemplateSeedRunner] No OFFICIAL templates found, starting seed initialization...");
        try {
            List<Map<String, Object>> seeds = loadSeedData();
            OffsetDateTime now = OffsetDateTime.now();

            for (Map<String, Object> seed : seeds) {
                String code = (String) seed.get("code");
                Template t = new Template();
                t.setId(UUID.randomUUID());
                t.setScope(Template.SCOPE_OFFICIAL);
                t.setWorkspaceId(null);
                t.setOwnerUserId(null);
                t.setCategory((String) seed.get("category"));
                t.setScenario((String) seed.get("scenario"));
                t.setPersona((String) seed.get("persona"));
                t.setTitle((String) seed.get("title"));
                t.setBodyTemplate((String) seed.get("bodyTemplate"));
                t.setVariables(serializeVariables(seed.get("variables")));
                t.setDefaultGoal((String) seed.getOrDefault("defaultGoal", "engagement"));
                t.setDefaultTone((String) seed.getOrDefault("defaultTone", "friendly"));
                t.setVersion(1);
                t.setStatus(Template.STATUS_ACTIVE);
                t.setUseCount(0);
                t.setCreatedAt(now);
                t.setUpdatedAt(now);

                templateMapper.insert(t);

                // Write v1 snapshot
                saveVersionSnapshot(t);

                log.info("[TemplateSeedRunner] Seeded template: {} ({})", code, t.getId());
            }

            log.info("[TemplateSeedRunner] Successfully seeded {} official templates.", seeds.size());
        } catch (Exception e) {
            log.error("[TemplateSeedRunner] Failed to seed official templates", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadSeedData() throws Exception {
        ClassPathResource resource = new ClassPathResource("data/templates/seed.json");
        try (InputStream is = resource.getInputStream()) {
            return objectMapper.readValue(is, new TypeReference<>() {});
        }
    }

    @SuppressWarnings("unchecked")
    private String serializeVariables(Object vars) throws Exception {
        if (vars instanceof List<?>) {
            return objectMapper.writeValueAsString(vars);
        }
        return "[]";
    }

    private void saveVersionSnapshot(Template t) throws Exception {
        Map<String, Object> snapshot = Map.of(
                "title", t.getTitle(),
                "bodyTemplate", t.getBodyTemplate(),
                "variables", objectMapper.readValue(t.getVariables(), List.class),
                "defaultGoal", t.getDefaultGoal() != null ? t.getDefaultGoal() : "",
                "defaultTone", t.getDefaultTone() != null ? t.getDefaultTone() : "",
                "category", t.getCategory() != null ? t.getCategory() : "",
                "scenario", t.getScenario() != null ? t.getScenario() : "",
                "persona", t.getPersona() != null ? t.getPersona() : ""
        );

        com.shortvideoscripagent.xhsagentyunying.domain.entity.TemplateVersion v =
                new com.shortvideoscripagent.xhsagentyunying.domain.entity.TemplateVersion();
        v.setId(UUID.randomUUID());
        v.setTemplateId(t.getId());
        v.setVersionNo(1);
        v.setSnapshot(objectMapper.writeValueAsString(snapshot));
        v.setPublishedAt(t.getCreatedAt());
        v.setPublishedBy(null);  // 系统自动初始化，无发布人
        versionMapper.insert(v);
    }
}
