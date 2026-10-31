package com.shortvideoscripagent.xhsagentyunying.billing.invoice.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.invoice.domain.entity.Invoice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.UUID;

/**
 * S4-4 发票 Mapper。
 */
@Mapper
public interface InvoiceMapper extends BaseMapper<Invoice> {

    /**
     * 按工作区 + 幂等键查发票。
     */
    @Select("""
            SELECT * FROM app.invoice
            WHERE workspace_id = #{workspaceId}
              AND idempotency_key = #{idempotencyKey}
            LIMIT 1
            """)
    Invoice selectByIdempotencyKey(@Param("workspaceId") String workspaceId,
                                    @Param("idempotencyKey") String idempotencyKey);

    /**
     * 按订单查「有效」发票（issued / red_flushed）。
     * <p>
     * 用于「只能对已支付且未开票订单开票」以及「退款需红冲」两条校验。
     * </p>
     */
    @Select("""
            SELECT * FROM app.invoice
            WHERE order_id = #{orderId}
              AND status IN ('issued', 'red_flushed')
            ORDER BY created_at DESC
            LIMIT 1
            """)
    Invoice selectActiveByOrderId(@Param("orderId") UUID orderId);

    /**
     * 按幂等键查发票（<b>不限定工作区</b>）。
     * <p>
     * <b>用途</b>：幂等重放检测。幂等键通常是全局唯一（UUID），
     * 因此<b>先</b>按幂等键回读既有发票，可以做到「重放请求不依赖订单查询」——
     * 避免订单状态已变时重放反而报错。
     * </p>
     */
    @Select("""
            SELECT * FROM app.invoice
            WHERE idempotency_key = #{idempotencyKey}
            LIMIT 1
            """)
    Invoice selectByIdempotencyKeyOnly(@Param("idempotencyKey") String idempotencyKey);

    /**
     * 原发票标记为「已被红冲」，并回填红冲记录 id。
     * <p>
     * 条件更新 {@code status = 'issued'}：保证一张发票只会被红冲一次。
     * </p>
     */
    @Update("""
            UPDATE app.invoice
            SET status = 'red_flushed',
                red_flush_invoice_id = #{redFlushInvoiceId},
                updated_at = NOW()
            WHERE id = #{id} AND status = 'issued'
            """)
    int compareAndSetRedFlushed(@Param("id") UUID id,
                                @Param("redFlushInvoiceId") UUID redFlushInvoiceId);

    /**
     * 回写 PDF 存储路径。
     */
    @Update("""
            UPDATE app.invoice
            SET pdf_path = #{pdfPath},
                updated_at = NOW()
            WHERE id = #{id}
            """)
    int updatePdfPath(@Param("id") UUID id, @Param("pdfPath") String pdfPath);

    /**
     * 统计发票号序列（按年月取当前最大序号，用于生成下一张发票号）。
     */
    @Select("""
            SELECT COUNT(*) FROM app.invoice
            WHERE invoice_no LIKE #{prefix} || '%'
            """)
    long countByInvoiceNoPrefix(@Param("prefix") String prefix);
}
