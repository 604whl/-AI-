package com.shortvideoscripagent.xhsagentyunying.billing.statement.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.statement.domain.entity.BillingStatementItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * S4-3 账单行 Mapper。
 */
@Mapper
public interface BillingStatementItemMapper extends BaseMapper<BillingStatementItem> {

    /**
     * 取账单的所有行（按创建顺序）。
     */
    @Select("""
            SELECT * FROM app.billing_statement_item
            WHERE statement_id = CAST(#{statementId} AS VARCHAR)
            ORDER BY unit ASC
            """)
    List<BillingStatementItem> selectByStatement(@Param("statementId") String statementId);
}
