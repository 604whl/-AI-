package com.shortvideoscripagent.xhsagentyunying.dto.import_;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class CsvImportResponse {
    private Long logId;
    private Integer totalRows;
    private Integer importedRows;
    private Integer skippedRows;
    private Integer errorRows;
    private String status;
    private List<String> errors;
}