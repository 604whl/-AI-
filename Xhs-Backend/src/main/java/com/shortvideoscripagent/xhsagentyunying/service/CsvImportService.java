package com.shortvideoscripagent.xhsagentyunying.service;

import com.shortvideoscripagent.xhsagentyunying.common.api.WorkspaceContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.CsvImportLog;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.CsvImportLogMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.import_.CsvImportRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.import_.CsvImportResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.asset.AssetCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.topic.TopicCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.topic.TopicDto;
import com.shortvideoscripagent.xhsagentyunying.service.MetricSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsvImportService {

    private static final int CODE_BAD_REQUEST = 40060;
    private static final int MAX_ERRORS = 10;

    private final CsvImportLogMapper importLogMapper;
    private final MetricSnapshotService metricSnapshotService;
    private final ContentAssetService contentAssetService;
    private final TopicPoolService topicPoolService;

    /**
     * CSV 导入入口。
     * <p>
     * <b>刻意不加 {@code @Transactional}</b>：批量导入是「逐行独立」的语义——
     * 单行写库失败只记 errorCount，不影响其它行，也不应把整批回滚。
     * 若外层开事务，PostgreSQL 中任一语句失败即把整个事务置为 aborted，
     * 后续所有行都会以 "current transaction is aborted" 失败，
     * 表现为「第一行出错 → 整批只导入 0 条」，与逐行错误明细的设计相悖。
     * </p>
     * <p>
     * 每行的写库调用（{@code createDraftInternal} / {@code createInternal}）本身带
     * {@code @Transactional}，在无外层事务时各自独立提交，既保证单行原子性
     * （资产 + 版本快照同生共死），又保证失败行不影响其它行。
     * </p>
     */
    public CsvImportResponse importCsv(Long userId, CsvImportRequest request) {
        String wsIdStr = WorkspaceContext.getWorkspaceId();
        if (wsIdStr == null || wsIdStr.isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "workspace_id_required");
        }

        byte[] csvBytes;
        try {
            csvBytes = Base64.getDecoder().decode(request.getCsvContent().trim());
        } catch (Exception e) {
            throw new BusinessException(CODE_BAD_REQUEST, "invalid_base64_csv");
        }
        String csvText = new String(csvBytes, StandardCharsets.UTF_8);

        String[] lines = csvText.split("\\r?\\n");
        if (lines.length < 2) {
            throw new BusinessException(CODE_BAD_REQUEST, "csv_must_have_header_and_data");
        }

        String[] headers = splitCsvLine(lines[0]).toArray(new String[0]);
        List<String[]> dataRows = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            try {
                dataRows.add(splitCsvLine(line).toArray(new String[0]));
            } catch (Exception e) {
                if (errors.size() < MAX_ERRORS) {
                    errors.add("Row " + (i + 1) + ": parse failed — " + e.getMessage());
                }
            }
        }

        int imported = 0, skipped = 0, errorCount = 0;
        if ("ASSET".equals(request.getImportType())) {
            int[] r = importAssets(userId, wsIdStr, headers, dataRows, errors);
            imported = r[0]; skipped = r[1]; errorCount = r[2];
        } else if ("TOPIC".equals(request.getImportType())) {
            int[] r = importTopics(userId, wsIdStr, headers, dataRows, errors);
            imported = r[0]; skipped = r[1]; errorCount = r[2];
        } else if ("METRIC".equals(request.getImportType())) {
            int[] r = importMetrics(userId, wsIdStr, request, csvText, headers, dataRows, errors);
            imported = r[0]; skipped = r[1]; errorCount = r[2];
        } else {
            throw new BusinessException(CODE_BAD_REQUEST, "invalid_import_type");
        }

        CsvImportLog csvLog = new CsvImportLog();
        csvLog.setUserId(userId);
        csvLog.setWorkspaceId(wsIdStr);
        csvLog.setImportType(request.getImportType());
        csvLog.setFilename(request.getFilename());
        csvLog.setTotalRows(dataRows.size());
        csvLog.setImportedRows(imported);
        csvLog.setSkippedRows(skipped);
        csvLog.setErrorRows(errorCount);
        csvLog.setStatus(errors.isEmpty() ? "completed" : "completed_with_errors");
        csvLog.setErrorDetail(errors.isEmpty() ? null : (errors.size() > MAX_ERRORS ? "..." : String.join("; ", errors)));
        csvLog.setCreatedAt(OffsetDateTime.now());
        importLogMapper.insert(csvLog);

        log.info("CSV import: type={}, total={}, imported={}, skipped={}, errors={}",
                request.getImportType(), dataRows.size(), imported, skipped, errorCount);

        return CsvImportResponse.builder()
                .logId(csvLog.getId())
                .totalRows(dataRows.size())
                .importedRows(imported)
                .skippedRows(skipped)
                .errorRows(errorCount)
                .status(csvLog.getStatus())
                .errors(errors.stream().limit(MAX_ERRORS).toList())
                .build();
    }

    /**
     * L1：CSV 导入内容资产（真实落库）。
     * <p>
     * 逐行调用 {@link ContentAssetService#createDraftInternal} 创建草稿资产；
     * title 为空 → skipped；写库异常 → errorCount++ 并记录明细（MAX_ERRORS 截断）；
     * 只有真正写库成功才 imported++。
     * </p>
     */
    private int[] importAssets(Long userId, String wsIdStr, String[] headers, List<String[]> rows, List<String> errors) {
        int imported = 0, skipped = 0, errorCount = 0;
        int titleIdx = findColumn(headers, "title");
        if (titleIdx < 0) {
            errors.add("CSV must have a 'title' column");
            return new int[]{0, 0, rows.size()};
        }
        int bodyIdx = findColumn(headers, "body");
        int personaIdx = findColumn(headers, "persona");
        int scenarioIdx = findColumn(headers, "scenario");
        int industryIdx = findColumn(headers, "industry");
        int platformIdx = findColumn(headers, "platform");
        // account_id 同时兼容 accountId 两种写法（CSV 生态两种都很常见）
        int accountIdx = findColumn(headers, "account_id");
        if (accountIdx < 0) accountIdx = findColumn(headers, "accountId");
        int sourceTaskIdx = findColumn(headers, "sourceTaskId");
        if (sourceTaskIdx < 0) sourceTaskIdx = findColumn(headers, "source_task_id");
        int templateIdx = findColumn(headers, "templateId");
        if (templateIdx < 0) templateIdx = findColumn(headers, "template_id");

        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            try {
                if (titleIdx >= row.length) { errors.add("Row " + (i + 2) + ": title missing"); errorCount++; continue; }
                if (row[titleIdx].trim().isEmpty()) { skipped++; continue; }

                AssetCreateRequest req = new AssetCreateRequest();
                req.setTitle(row[titleIdx].trim());
                req.setBody(valueAt(row, bodyIdx));
                req.setPersona(valueAt(row, personaIdx));
                req.setScenario(valueAt(row, scenarioIdx));
                req.setIndustry(valueAt(row, industryIdx));
                req.setPlatform(valueAt(row, platformIdx));
                req.setAccountId(valueAt(row, accountIdx));
                req.setSourceTaskId(valueAt(row, sourceTaskIdx));
                req.setTemplateId(valueAt(row, templateIdx));

                contentAssetService.createDraftInternal(userId, wsIdStr, req);
                imported++;
            } catch (Exception e) {
                if (errors.size() < MAX_ERRORS) errors.add("Row " + (i + 2) + ": " + e.getMessage());
                errorCount++;
            }
        }
        return new int[]{imported, skipped, errorCount};
    }

    /**
     * L1：CSV 导入选题池（真实落库）。
     * <p>
     * 逐行调用 {@link TopicPoolService#createInternal} 创建选题；命中去重时接口返回 null → 计入 skipped；
     * 写库异常 → errorCount++ 并记录明细；只有真正写库成功才 imported++。
     * </p>
     */
    private int[] importTopics(Long userId, String wsIdStr, String[] headers, List<String[]> rows, List<String> errors) {
        int imported = 0, skipped = 0, errorCount = 0;
        int titleIdx = findColumn(headers, "title");
        if (titleIdx < 0) {
            errors.add("CSV must have a 'title' column");
            return new int[]{0, 0, rows.size()};
        }
        int sourceIdx = findColumn(headers, "source");
        int heatIdx = findColumn(headers, "heatScore");
        if (heatIdx < 0) heatIdx = findColumn(headers, "heat_score");
        int tagsIdx = findColumn(headers, "tags");
        int personaIdx = findColumn(headers, "targetPersona");
        if (personaIdx < 0) personaIdx = findColumn(headers, "target_persona");
        int dateIdx = findColumn(headers, "scheduledDate");
        if (dateIdx < 0) dateIdx = findColumn(headers, "scheduled_date");

        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            try {
                if (titleIdx >= row.length) { errors.add("Row " + (i + 2) + ": title missing"); errorCount++; continue; }
                if (row[titleIdx].trim().isEmpty()) { skipped++; continue; }

                TopicCreateRequest req = new TopicCreateRequest();
                req.setTitle(row[titleIdx].trim());
                String source = valueAt(row, sourceIdx);
                req.setSource(source == null || source.isBlank() ? "csv_import" : source.trim());
                String heat = valueAt(row, heatIdx);
                if (heat != null && !heat.isBlank()) {
                    try {
                        req.setHeatScore((int) Math.round(Double.parseDouble(heat.trim())));
                    } catch (NumberFormatException ignore) {
                        // heat_score 非法时按 0 处理，不算错误行
                    }
                }
                String tags = valueAt(row, tagsIdx);
                if (tags != null && !tags.isBlank()) {
                    req.setTags(List.of(tags.trim().split("\\s*[|;]\\s*")));
                }
                req.setTargetPersona(valueAt(row, personaIdx));
                String scheduled = valueAt(row, dateIdx);
                if (scheduled != null && !scheduled.isBlank()) {
                    try {
                        req.setScheduledDate(java.time.LocalDate.parse(scheduled.trim()));
                    } catch (Exception ignore) {
                        // 日期非法时忽略，不算错误行
                    }
                }

                TopicDto created = topicPoolService.createInternal(wsIdStr, userId, req);
                if (created == null) {
                    skipped++;  // 去重命中
                } else {
                    imported++;
                }
            } catch (Exception e) {
                if (errors.size() < MAX_ERRORS) errors.add("Row " + (i + 2) + ": " + e.getMessage());
                errorCount++;
            }
        }
        return new int[]{imported, skipped, errorCount};
    }

    /** 取列值；列不存在或越界返回 null。 */
    private String valueAt(String[] row, int idx) {
        if (idx < 0 || idx >= row.length) return null;
        String v = row[idx].trim();
        return v.isEmpty() ? null : v;
    }

    /**
     * S3-5：CSV 导入指标快照（import_type = 'METRIC'）。
     * <p>
     * 复用既有 csv_import_log 框架；表头白名单由 {@link com.shortvideoscripagent.xhsagentyunying.service.MetricSnapshotService}
     * 统一校验（未知列直接拒绝 → 40062 CSV_SCHEMA_INVALID）；失败行不影响成功行；
     * 错误明细含行号 + 原因（沿用既有 MAX_ERRORS 截断逻辑）。
     * </p>
     */
    private int[] importMetrics(Long userId, String wsIdStr, CsvImportRequest request,
                                String csvText, String[] headers, List<String[]> rows, List<String> errors) {
        String platformCode = request.getPlatformCode() == null ? null : request.getPlatformCode().trim();
        if (platformCode == null || platformCode.isBlank()) {
            platformCode = "XIAOHONGSHU";
        }
        String accountId = request.getAccountId() == null ? null : request.getAccountId().trim();
        try {
            MetricSnapshotService.CsvImportOutcome outcome =
                    metricSnapshotService.importMetricsCsv(wsIdStr, userId, platformCode, accountId,
                            csvText, errors, MAX_ERRORS);
            return new int[]{outcome.imported(), outcome.skipped(), outcome.errorRows()};
        } catch (BusinessException be) {
            // 表头非法等致命错误：错误明细已写入 errors，直接向上抛出（不吞掉）
            throw be;
        }
    }

    private int findColumn(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    /**
     * 轻量 CSV 行切分（支持双引号包裹与转义双引号 {@code ""}）。
     * <p>
     * 与 {@link MetricSnapshotService} 的实现保持一致；不处理跨行引号
     * （CSV 单元格内含换行属于少见场景，超出本导入器约定）。
     * </p>
     */
    private List<String> splitCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString());
        return cells;
    }
}