package com.shortvideoscripagent.xhsagentyunying.service.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ContentAsset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * ZIP 打包器（P0-5）。
 * <p>
 * 打包内容：单个资产的 markdown 文件 + 原图附件 + report.json 元数据。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZipPacker {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private final MarkdownRenderer markdownRenderer;
    private final ObjectMapper objectMapper;

    /**
     * 打包单个资产为 ZIP。
     *
     * @param asset         资产
     * @param coverBytes    封面图字节数组（可为 null）
     * @param coverFileName 封面图文件名（含扩展名，如 cover.jpg）
     * @param coverMimeType 封面图 MIME 类型
     * @return ZIP 文件字节数组
     */
    public byte[] pack(
            ContentAsset asset,
            byte[] coverBytes,
            String coverFileName,
            String coverMimeType
    ) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(8192);
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            String slug = slugify(asset.getTitle());
            String mdFileName = slug + ".md";

            // ── 1. Markdown 文件 ───────────────────────────────────────────
            String coverUrl = coverBytes != null
                    ? "./" + coverFileName
                    : null;
            String markdownContent = markdownRenderer.renderAsString(asset, coverUrl);
            addEntry(zos, mdFileName, markdownContent.getBytes(StandardCharsets.UTF_8));

            // ── 2. 封面图片 ────────────────────────────────────────────────
            if (coverBytes != null && coverBytes.length > 0) {
                addEntry(zos, coverFileName, coverBytes);
            }

            // ── 3. report.json 元数据 ─────────────────────────────────────
            Map<String, Object> meta = buildMetadata(asset);
            byte[] metaJson = objectMapper.writeValueAsBytes(meta);
            addEntry(zos, "report.json", metaJson);

            zos.finish();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to pack ZIP for asset {}: {}", asset.getId(), e.getMessage(), e);
            throw new RuntimeException("ZIP packing failed", e);
        }
    }

    /**
     * 打包多个资产为 ZIP。
     *
     * @param assets       资产列表（每个资产对应一个文件夹）
     * @param coverBytesList 每个资产的封面图（顺序与 assets 对应，可含 null）
     * @return ZIP 文件字节数组
     */
    public byte[] packMultiple(
            List<ContentAsset> assets,
            List<byte[]> coverBytesList
    ) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(8192 * assets.size());
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            for (int i = 0; i < assets.size(); i++) {
                ContentAsset asset = assets.get(i);
                String slug = slugify(asset.getTitle());
                String base = slug + "/";
                byte[] coverBytes = i < coverBytesList.size() ? coverBytesList.get(i) : null;
                String coverFileName = "cover.jpg";

                // Markdown
                String coverUrl = coverBytes != null ? "./" + coverFileName : null;
                String md = markdownRenderer.renderAsString(asset, coverUrl);
                addEntry(zos, base + slug + ".md", md.getBytes(StandardCharsets.UTF_8));

                // 封面图
                if (coverBytes != null && coverBytes.length > 0) {
                    addEntry(zos, base + coverFileName, coverBytes);
                }

                // 每个资产的元数据
                Map<String, Object> meta = buildMetadata(asset);
                addEntry(zos, base + "report.json",
                        objectMapper.writeValueAsBytes(meta));
            }

            zos.finish();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to pack multiple assets ZIP: {}", e.getMessage(), e);
            throw new RuntimeException("Multiple asset ZIP packing failed", e);
        }
    }

    private Map<String, Object> buildMetadata(ContentAsset asset) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("id", asset.getId().toString());
        meta.put("title", asset.getTitle());
        meta.put("persona", asset.getPersona());
        meta.put("scenario", asset.getScenario());
        meta.put("industry", asset.getIndustry());
        meta.put("status", asset.getStatus());
        meta.put("platform", asset.getPlatform());
        meta.put("accountId", asset.getAccountId());
        meta.put("version", asset.getCurrentVersion());
        meta.put("createdAt", asset.getCreatedAt() != null
                ? DTF.format(asset.getCreatedAt()) : null);
        meta.put("updatedAt", asset.getUpdatedAt() != null
                ? DTF.format(asset.getUpdatedAt()) : null);
        meta.put("exportedAt", DTF.format(OffsetDateTime.now()));
        return meta;
    }

    private void addEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data)) {
            bais.transferTo(zos);
        }
        zos.closeEntry();
    }

    /** 简单 slugify：去除空格和特殊字符 */
    private String slugify(String title) {
        if (title == null) return "untitled";
        return title
                .replaceAll("[\\\\/:*?\"<>|\\s]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "")
                .substring(0, Math.min(title.length(), 80));
    }
}
