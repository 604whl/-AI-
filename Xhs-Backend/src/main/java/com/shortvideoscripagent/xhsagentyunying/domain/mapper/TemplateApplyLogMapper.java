package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.TemplateApplyLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * TemplateApplyLog Mapper（P0-4）。
 */
@Mapper
public interface TemplateApplyLogMapper extends BaseMapper<TemplateApplyLog> {

    /**
     * 按日聚合某模板的引用次数。
     * 返回每日引用数量。
     */
    @Select("""
            SELECT DATE(created_at) as day, COUNT(*) as count
            FROM app.template_apply_log
            WHERE template_id = #{templateId}
              AND created_at >= #{from}
            GROUP BY DATE(created_at)
            ORDER BY day DESC
            """)
    List<DailyStat> selectDailyStats(
            @Param("templateId") UUID templateId,
            @Param("from") OffsetDateTime from
    );

    /**
     * 每日统计 DTO（接口返回用）。
     */
    interface DailyStat {
        String getDay();
        Long getCount();
    }
}
