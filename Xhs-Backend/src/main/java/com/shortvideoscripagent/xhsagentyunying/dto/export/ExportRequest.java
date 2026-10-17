package com.shortvideoscripagent.xhsagentyunying.dto.export;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 导出请求（P0-5）。
 */
@Data
public class ExportRequest {

    /**
     * 导出格式：md / docx / zip / csv
     */
    @NotBlank(message = "format is required")
    @Pattern(regexp = "^(md|docx|zip|csv)$", message = "format must be one of: md, docx, zip, csv")
    private String format;
}
