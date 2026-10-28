package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName("csv_import_log")
public class CsvImportLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** Workspace ID（VARCHAR(32)，与 app.workspace.id 一致；V49 由 UUID 归一） */
    private String workspaceId;
    private String importType;
    private String filename;
    private Integer totalRows;
    private Integer importedRows;
    private Integer skippedRows;
    private Integer errorRows;
    private String status;
    private String errorDetail;
    private OffsetDateTime createdAt;
}