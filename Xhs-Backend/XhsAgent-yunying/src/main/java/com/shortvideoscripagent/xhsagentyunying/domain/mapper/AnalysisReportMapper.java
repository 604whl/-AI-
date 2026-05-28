package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.AnalysisReport;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AnalysisReportMapper extends BaseMapper<AnalysisReport> {

    @Insert("""
            INSERT INTO analysis_report (task_id, report_json, compliance_warnings, created_at)
            VALUES (#{taskId}, CAST(#{reportJson} AS jsonb), CAST(#{complianceWarnings} AS jsonb), #{createdAt})
            """)
    void insertJsonb(AnalysisReport report);
}
