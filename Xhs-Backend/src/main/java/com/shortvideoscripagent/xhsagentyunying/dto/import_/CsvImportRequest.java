package com.shortvideoscripagent.xhsagentyunying.dto.import_;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CsvImportRequest {
    @NotBlank
    @Pattern(regexp = "ASSET|TOPIC", message = "invalid_import_type")
    private String importType;

    @NotBlank
    private String csvContent;

    @Size(max = 256)
    private String filename;
}