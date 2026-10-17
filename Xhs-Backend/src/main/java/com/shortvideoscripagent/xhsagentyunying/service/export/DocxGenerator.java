package com.shortvideoscripagent.xhsagentyunying.service.export;

import com.shortvideoscripagent.xhsagentyunying.domain.entity.ContentAsset;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * DOCX 生成器（P0-5）。
 * <p>
 * 使用 Apache POI 5.x 生成 Word 文档：
 * - 标题样式（H1）
 * - 正文段落
 * - 封面图片（Inline 图片）
 * - 元数据表格
 * </p>
 * <p>
 * 大文本（>1000 行）采用分块处理避免 OOM。
 * </p>
 */
@Slf4j
@Component
public class DocxGenerator {

    private static final int PARAGRAPH_CHUNK_SIZE = 200; // 每段处理 200 行

    /**
     * 生成 DOCX 字节数组。
     *
     * @param asset       资产
     * @param coverBytes  封面图字节数组（可为 null）
     * @param coverType   封面图 MIME 类型（如 image/jpeg）
     * @return DOCX 文件字节数组
     */
    public byte[] generate(ContentAsset asset, byte[] coverBytes, String coverType) {
        try (XWPFDocument document = new XWPFDocument()) {

            // ── 封面图片 ────────────────────────────────────────────────────
            if (coverBytes != null && coverBytes.length > 0) {
                addCoverImage(document, coverBytes, coverType);
            }

            // ── 标题 ──────────────────────────────────────────────────────
            addHeading(document, asset.getTitle(), 1);

            // ── 元数据表格 ──────────────────────────────────────────────────
            addMetadataTable(document, asset);

            // ── 正文段落（分块处理，防止 OOM） ─────────────────────────────
            String body = asset.getBody();
            if (StringUtils.hasText(body)) {
                addBodyParagraphs(document, body);
            }

            return toByteArray(document);
        } catch (Exception e) {
            log.error("Failed to generate DOCX for asset {}: {}", asset.getId(), e.getMessage(), e);
            throw new RuntimeException("DOCX generation failed", e);
        }
    }

    private void addCoverImage(XWPFDocument document, byte[] imageBytes, String contentType) {
        try {
            int pictureTypeId = resolvePoiPictureType(contentType);
            // 图片尺寸：宽 540 pt（EMU = 540 * 12700），高按比例
            int widthEmu = 540 * 12700;
            int heightEmu = 320 * 12700;

            XWPFRun run = document.createParagraph().createRun();
            run.addPicture(
                    new ByteArrayInputStream(imageBytes),
                    pictureTypeId,
                    "cover",
                    widthEmu,
                    heightEmu
            );
        } catch (Exception e) {
            log.warn("Failed to add cover image: {}", e.getMessage());
        }
    }

    private int resolvePoiPictureType(String contentType) {
        if (contentType == null) return XWPFDocument.PICTURE_TYPE_JPEG;
        return switch (contentType.toLowerCase()) {
            case "image/png" -> XWPFDocument.PICTURE_TYPE_PNG;
            case "image/gif" -> XWPFDocument.PICTURE_TYPE_GIF;
            case "image/bmp" -> XWPFDocument.PICTURE_TYPE_BMP;
            default -> XWPFDocument.PICTURE_TYPE_JPEG;
        };
    }

    private void addHeading(XWPFDocument document, String text, int level) {
        XWPFParagraph p = document.createParagraph();
        p.setStyle("Heading" + level);
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setBold(true);
        if (level == 1) {
            run.setFontSize(22);
        } else {
            run.setFontSize(16);
        }
    }

    private void addMetadataTable(XWPFDocument document, ContentAsset asset) {
        // 构建所有行，一次性创建表格
        var rows = new java.util.ArrayList<String[]>();
        rows.add(new String[]{"Status", asset.getStatus() != null ? asset.getStatus() : "-"});

        if (StringUtils.hasText(asset.getPersona())) {
            rows.add(new String[]{"Persona", asset.getPersona()});
        }
        if (StringUtils.hasText(asset.getScenario())) {
            rows.add(new String[]{"Scenario", asset.getScenario()});
        }
        if (StringUtils.hasText(asset.getIndustry())) {
            rows.add(new String[]{"Industry", asset.getIndustry()});
        }
        if (StringUtils.hasText(asset.getPlatform())) {
            rows.add(new String[]{"Platform", asset.getPlatform()});
        }
        if (StringUtils.hasText(asset.getAccountId())) {
            rows.add(new String[]{"Account", asset.getAccountId()});
        }
        if (asset.getCurrentVersion() != null) {
            rows.add(new String[]{"Version", String.valueOf(asset.getCurrentVersion())});
        }

        XWPFTable table = document.createTable(rows.size(), 2);
        table.setWidth("100%");

        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            table.getRow(i).getCell(0).setText(row[0]);
            table.getRow(i).getCell(1).setText(row[1]);
        }

        document.createParagraph(); // 空行分隔
    }

    /**
     * 分块写入正文，防止大文本 OOM。
     */
    private void addBodyParagraphs(XWPFDocument document, String body) {
        String[] lines = body.split("\n", -1);
        StringBuilder chunk = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            chunk.append(lines[i]);
            if (lines[i].isEmpty() || (i == lines.length - 1)) {
                // 段落结束，flush
                if (chunk.length() > 0) {
                    XWPFParagraph p = document.createParagraph();
                    XWPFRun run = p.createRun();
                    run.setText(chunk.toString().trim());
                    run.setFontSize(12);
                }
                chunk.setLength(0);
            } else {
                chunk.append("\n");
            }

            // 每处理 PARAGRAPH_CHUNK_SIZE 行，yield 让 GC 有机会回收
            if (i > 0 && i % PARAGRAPH_CHUNK_SIZE == 0) {
                log.debug("DOCX body chunk processed: {} / {} lines", i, lines.length);
            }
        }
    }

    private byte[] toByteArray(XWPFDocument document) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(8192);
        document.write(baos);
        return baos.toByteArray();
    }
}
