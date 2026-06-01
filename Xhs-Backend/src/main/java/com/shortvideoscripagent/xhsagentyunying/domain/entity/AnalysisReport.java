package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("analysis_report")
public class AnalysisReport {

    @TableId
    private String taskId;

    private String reportJson;

    private String coverAnalysis;

    private String complianceWarnings;

    private OffsetDateTime createdAt;
}
