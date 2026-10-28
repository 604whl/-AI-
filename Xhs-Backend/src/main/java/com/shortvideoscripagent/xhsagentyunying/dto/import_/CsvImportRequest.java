package com.shortvideoscripagent.xhsagentyunying.dto.import_;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CsvImportRequest {
    @NotBlank
    @Pattern(regexp = "ASSET|TOPIC|METRIC", message = "invalid_import_type")
    private String importType;

    @NotBlank
    private String csvContent;

    @Size(max = 256)
    private String filename;

    /** S3-5：import_type=METRIC 时的平台代码 */
    @Size(max = 32)
    private String platformCode;

    /** S3-5：import_type=METRIC 时的平台账号 ID（可空） */
    @Size(max = 64)
    private String accountId;
}