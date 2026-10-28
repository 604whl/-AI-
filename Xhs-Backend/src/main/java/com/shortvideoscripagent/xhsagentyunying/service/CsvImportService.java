package com.shortvideoscripagent.xhsagentyunying.service;

import com.shortvideoscripagent.xhsagentyunying.common.api.WorkspaceContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.CsvImportLog;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.CsvImportLogMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.import_.CsvImportRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.import_.CsvImportResponse;
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

    @Transactional
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

        String[] headers = lines[0].split(",");
        List<String[]> dataRows = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            try {
                dataRows.add(line.split(","));
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

    private int[] importAssets(Long userId, String wsIdStr, String[] headers, List<String[]> rows, List<String> errors) {
        int imported = 0, skipped = 0, errorCount = 0;
        int titleIdx = findColumn(headers, "title");
        if (titleIdx < 0) {
            errors.add("CSV must have a 'title' column");
            return new int[]{0, 0, rows.size()};
        }
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            try {
                if (titleIdx >= row.length) { errors.add("Row " + (i + 2) + ": title missing"); errorCount++; continue; }
                if (row[titleIdx].trim().isEmpty()) { skipped++; continue; }
                // TODO: delegate to ContentAssetService.createDraftInternal
                imported++;
            } catch (Exception e) {
                if (errors.size() < MAX_ERRORS) errors.add("Row " + (i + 2) + ": " + e.getMessage());
                errorCount++;
            }
        }
        return new int[]{imported, skipped, errorCount};
    }

    private int[] importTopics(Long userId, String wsIdStr, String[] headers, List<String[]> rows, List<String> errors) {
        int imported = 0, skipped = 0, errorCount = 0;
        int titleIdx = findColumn(headers, "title");
        if (titleIdx < 0) {
            errors.add("CSV must have a 'title' column");
            return new int[]{0, 0, rows.size()};
        }
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            try {
                if (titleIdx >= row.length) { errors.add("Row " + (i + 2) + ": title missing"); errorCount++; continue; }
                if (row[titleIdx].trim().isEmpty()) { skipped++; continue; }
                // TODO: delegate to TopicPoolService
                imported++;
            } catch (Exception e) {
                if (errors.size() < MAX_ERRORS) errors.add("Row " + (i + 2) + ": " + e.getMessage());
                errorCount++;
            }
        }
        return new int[]{imported, skipped, errorCount};
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
}