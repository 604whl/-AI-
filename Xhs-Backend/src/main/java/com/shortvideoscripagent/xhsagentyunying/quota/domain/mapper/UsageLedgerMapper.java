package com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.quota.domain.entity.UsageLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * UsageLedger Mapper。
 */
@Mapper
public interface UsageLedgerMapper extends BaseMapper<UsageLedger> {

    /**
     * 按 ledger id 查询状态。
     */
    @Select("SELECT status FROM app.usage_ledger WHERE id = #{id}")
    String selectStatusById(@Param("id") java.util.UUID id);

    /**
     * 将 ledger 标记为已冲正（REFUNDED）。
     */
    @Update("""
            UPDATE app.usage_ledger
            SET status = 'REFUNDED'
            WHERE id = #{id}
              AND status = 'CONSUMED'
            """)
    int markRefunded(@Param("id") java.util.UUID id);
}
