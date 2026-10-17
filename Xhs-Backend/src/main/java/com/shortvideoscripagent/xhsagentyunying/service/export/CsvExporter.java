package com.shortvideoscripagent.xhsagentyunying.service.export;

import com.shortvideoscripagent.xhsagentyunying.domain.entity.ContentAsset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * CSV 导出器（P0-5）。
 * <p>
 * 批量资产列表导出，UTF-8 BOM 编码，兼容 Excel 中文显示。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CsvExporter {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    // CSV BOM（Excel UTF-8 识别）
    private static final byte[] UTF8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    // 列头
    private static final String[] HEADERS = {
            "ID", "Title", "Status", "Persona", "Scenario",
            "Industry", "Platform", "Account", "Version",
            "Created At", "Updated At"
    };

    /**
     * 导出资产列表为 CSV。
     *
     * @param assets 资产列表
     * @return UTF-8 BOM 编码的 CSV 字节数组
     */
    public byte[] export(List<ContentAsset> assets) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(8192)) {
            // 写入 UTF-8 BOM（让 Excel 正确识别中文）
            baos.write(UTF8_BOM);

            // 列头
            writeLine(baos, HEADERS);

            // 数据行（流式处理，避免一次性加载）
            int count = 0;
            for (ContentAsset asset : assets) {
                String[] row = {
                        asset.getId().toString(),
                        csvEscape(asset.getTitle()),
                        asset.getStatus(),
                        nullOr(asset.getPersona()),
                        nullOr(asset.getScenario()),
                        nullOr(asset.getIndustry()),
                        nullOr(asset.getPlatform()),
                        nullOr(asset.getAccountId()),
                        asset.getCurrentVersion() != null
                                ? String.valueOf(asset.getCurrentVersion()) : "",
                        asset.getCreatedAt() != null
                                ? DTF.format(asset.getCreatedAt()) : "",
                        asset.getUpdatedAt() != null
                                ? DTF.format(asset.getUpdatedAt()) : ""
                };
                writeLine(baos, row);
                count++;

                // 每 500 行打点日志（避免大数据量 OOM）
                if (count % 500 == 0) {
                    log.debug("CSV export progress: {} / {} assets", count, assets.size());
                }
            }

            log.info("CSV exported {} assets", count);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("CSV export failed: {}", e.getMessage(), e);
            throw new RuntimeException("CSV export failed", e);
        }
    }

    private void writeLine(ByteArrayOutputStream baos, String[] fields) throws IOException {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                baos.write(',');
            }
            if (fields[i] != null) {
                baos.write(fields[i].getBytes(StandardCharsets.UTF_8));
            }
        }
        baos.write('\n');
    }

    /** CSV 双引号转义：字段含逗号/换行/双引号时加双引号包裹 */
    private String csvEscape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private String nullOr(String s) {
        return s != null ? s : "";
    }
}
