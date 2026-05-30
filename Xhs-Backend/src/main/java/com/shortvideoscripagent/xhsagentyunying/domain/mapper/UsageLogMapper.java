package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.UsageLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UsageLogMapper extends BaseMapper<UsageLog> {

    @Select("""
            SELECT COUNT(*) FROM usage_log
            WHERE user_id = #{userId}
              AND action = #{action}
              AND created_at >= CURRENT_DATE
            """)
    int countTodayByUserAndAction(@Param("userId") Long userId, @Param("action") String action);
}
