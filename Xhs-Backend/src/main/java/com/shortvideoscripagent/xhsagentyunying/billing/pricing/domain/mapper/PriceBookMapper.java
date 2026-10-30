package com.shortvideoscripagent.xhsagentyunying.billing.pricing.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.pricing.domain.entity.PriceBook;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * S4-3 价格表 Mapper。
 */
@Mapper
public interface PriceBookMapper extends BaseMapper<PriceBook> {

    /**
     * 取某计量单位在指定时间点生效的价格行（effective_from &lt;= at，按 version 取最新）。
     * <p>
     * 显式 CAST 时间参数，避免 PostgreSQL 在混合类型比较时无法推断参数类型。
     * </p>
     */
    @Select("""
            SELECT * FROM app.price_book
            WHERE unit = CAST(#{unit} AS VARCHAR)
              AND region = CAST(#{region} AS VARCHAR)
              AND effective_from <= CAST(#{at} AS TIMESTAMPTZ)
            ORDER BY version DESC
            LIMIT 1
            """)
    PriceBook selectEffective(@Param("unit") String unit,
                              @Param("region") String region,
                              @Param("at") OffsetDateTime at);

    /**
     * 取某计量单位当前最新价格行（不限时间）。
     */
    @Select("""
            SELECT * FROM app.price_book
            WHERE unit = CAST(#{unit} AS VARCHAR)
              AND region = CAST(#{region} AS VARCHAR)
            ORDER BY version DESC
            LIMIT 1
            """)
    PriceBook selectLatest(@Param("unit") String unit, @Param("region") String region);

    /**
     * 取某计量单位指定套餐版本的价格行。
     */
    @Select("""
            SELECT * FROM app.price_book
            WHERE unit = CAST(#{unit} AS VARCHAR)
              AND region = CAST(#{region} AS VARCHAR)
              AND plan_version = CAST(#{planVersion} AS INT)
            LIMIT 1
            """)
    PriceBook selectByPlanVersion(@Param("unit") String unit,
                                  @Param("region") String region,
                                  @Param("planVersion") Integer planVersion);

    /**
     * 按价格行版本取成本单价（用于历史台账回算成本）。
     */
    @Select("""
            SELECT cost_per_unit FROM app.price_book
            WHERE unit = CAST(#{unit} AS VARCHAR)
              AND region = CAST(#{region} AS VARCHAR)
              AND version = CAST(#{version} AS INT)
            LIMIT 1
            """)
    java.math.BigDecimal selectCostByVersion(@Param("unit") String unit,
                                             @Param("region") String region,
                                             @Param("version") Integer version);

    /**
     * 取某计量单位的最大价格行版本（改价时生成新版本用）。
     */
    @Select("""
            SELECT COALESCE(MAX(version), 0) FROM app.price_book
            WHERE unit = CAST(#{unit} AS VARCHAR)
              AND region = CAST(#{region} AS VARCHAR)
            """)
    int selectMaxVersion(@Param("unit") String unit, @Param("region") String region);

    /**
     * 列出全部价格行（管理端对账用）。
     */
    @Select("""
            SELECT * FROM app.price_book
            ORDER BY unit ASC, version DESC
            """)
    List<PriceBook> selectAll();

    /**
     * 按 id 取价格行。
     */
    @Select("""
            SELECT * FROM app.price_book WHERE id = CAST(#{id} AS UUID)
            """)
    PriceBook selectByUuid(@Param("id") UUID id);
}
