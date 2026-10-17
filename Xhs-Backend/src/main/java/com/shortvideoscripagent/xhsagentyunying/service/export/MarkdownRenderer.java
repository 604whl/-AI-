package com.shortvideoscripagent.xhsagentyunying.service.export;

import com.shortvideoscripagent.xhsagentyunying.domain.entity.ContentAsset;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ContentAssetMedia;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Markdown 渲染器（P0-5）。
 * <p>
 * 输出格式：YAML frontmatter + Markdown 正文。
 * 图片默认输出为 Markdown 图片引用（URL）；若传入 base64，则内联嵌入。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarkdownRenderer {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /**
     * 渲染单个资产为 Markdown 字节数组。
     *
     * @param asset       资产
     * @param coverUrl   封面图 URL（可为 null）
     * @param inlineImage 是否内联 base64 图片（默认 false，外链引用）
     * @return UTF-8 编码的 Markdown 字节数组
     */
    public byte[] render(ContentAsset asset, String coverUrl, boolean inlineImage) {
        StringBuilder sb = new StringBuilder();

        // ── YAML Frontmatter ────────────────────────────────────────────────
        sb.append("---\n");
        sb.append("title: \"").append(escapeYaml(asset.getTitle())).append("\"\n");
        if (StringUtils.hasText(asset.getPersona())) {
            sb.append("persona: \"").append(asset.getPersona()).append("\"\n");
        }
        if (StringUtils.hasText(asset.getScenario())) {
            sb.append("scenario: \"").append(asset.getScenario()).append("\"\n");
        }
        if (StringUtils.hasText(asset.getIndustry())) {
            sb.append("industry: \"").append(asset.getIndustry()).append("\"\n");
        }
        sb.append("status: \"").append(asset.getStatus()).append("\"\n");
        if (StringUtils.hasText(asset.getPlatform())) {
            sb.append("platform: \"").append(asset.getPlatform()).append("\"\n");
        }
        if (StringUtils.hasText(asset.getAccountId())) {
            sb.append("account_id: \"").append(asset.getAccountId()).append("\"\n");
        }
        sb.append("version: ").append(asset.getCurrentVersion()).append("\n");
        sb.append("created_at: \"").append(asset.getCreatedAt() != null
                ? DTF.format(asset.getCreatedAt()) : "").append("\"\n");
        sb.append("updated_at: \"").append(asset.getUpdatedAt() != null
                ? DTF.format(asset.getUpdatedAt()) : "").append("\"\n");
        if (coverUrl != null) {
            sb.append("cover: \"").append(coverUrl).append("\"\n");
        }
        sb.append("---\n\n");

        // ── 正文 ────────────────────────────────────────────────────────────
        if (coverUrl != null) {
            sb.append("![cover](").append(coverUrl).append(")\n\n");
        }

        sb.append("# ").append(asset.getTitle()).append("\n\n");

        if (StringUtils.hasText(asset.getBody())) {
            sb.append(asset.getBody());
            if (!asset.getBody().endsWith("\n")) {
                sb.append("\n");
            }
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 渲染单个资产为 Markdown 字符串（供 ZIP 打包时使用）。
     */
    public String renderAsString(ContentAsset asset, String coverUrl) {
        return new String(render(asset, coverUrl, false), StandardCharsets.UTF_8);
    }

    /**
     * 渲染封面图 Markdown 图片行。
     */
    public String renderCoverImage(String coverUrl) {
        if (coverUrl == null) {
            return "";
        }
        return "![cover](" + coverUrl + ")\n";
    }

    private String escapeYaml(String s) {
        if (s == null) return "";
        // 双引号内转义
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
