package com.shortvideoscripagent.xhsagentyunying.service;

import com.shortvideoscripagent.xhsagentyunying.common.api.WorkspaceContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.CsvImportLog;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.CsvImportLogMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.import_.CsvImportRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.import_.CsvImportResponse;
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

    @Transactional
    public CsvImportResponse importCsv(Long userId, CsvImportRequest request) {
        String wsIdStr = WorkspaceContext.getWorkspaceId();
        if (wsIdStr == null || wsIdStr.isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "workspace_id_required");
        }
        UUID wsUuid = UUID.fromString(wsIdStr);

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
            int[] r = importAssets(userId, wsUuid, headers, dataRows, errors);
            imported = r[0]; skipped = r[1]; errorCount = r[2];
        } else if ("TOPIC".equals(request.getImportType())) {
            int[] r = importTopics(userId, wsUuid, headers, dataRows, errors);
            imported = r[0]; skipped = r[1]; errorCount = r[2];
        } else {
            throw new BusinessException(CODE_BAD_REQUEST, "invalid_import_type");
        }

        CsvImportLog csvLog = new CsvImportLog();
        csvLog.setUserId(userId);
        csvLog.setWorkspaceId(wsUuid);
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

    private int[] importAssets(Long userId, UUID wsUuid, String[] headers, List<String[]> rows, List<String> errors) {
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

    private int[] importTopics(Long userId, UUID wsUuid, String[] headers, List<String[]> rows, List<String> errors) {
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

    private int findColumn(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }
}