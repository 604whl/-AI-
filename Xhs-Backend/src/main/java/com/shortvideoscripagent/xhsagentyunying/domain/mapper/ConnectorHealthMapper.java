package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ConnectorHealth;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 连接器健康状态 Mapper。
 * <p>
 * 注意：{@code workspace_id} 为空表示"全局健康状态"。
 * PostgreSQL 的普通唯一索引不把 NULL 视为相等，因此本 Mapper 全部采用
 * "先 select 再 insert/update" 的方式（见 {@code ConnectorHealthService}），
 * 避免 NULL 参与唯一约束时产生重复行。
 */
@Mapper
public interface ConnectorHealthMapper extends BaseMapper<ConnectorHealth> {

    /**
     * 查询某连接器在指定 workspace 下的健康记录。
     * <p>
     * workspaceId 为 null / 空时只查全局记录；否则优先查该 workspace 的记录，
     * 查不到再回退到全局记录。
     */
    @Select("""
            SELECT * FROM app.connector_health
            WHERE connector_provider_code = #{providerCode}
              AND platform_code = #{platformCode}
              AND (
                    (CASE WHEN CAST(#{workspaceId} AS VARCHAR) IS NULL OR CAST(#{workspaceId} AS VARCHAR) = ''
                          THEN workspace_id IS NULL
                          ELSE (workspace_id = #{workspaceId} OR workspace_id IS NULL)
                     END)
                  )
            ORDER BY (workspace_id IS NULL) ASC
            LIMIT 1
            """)
    ConnectorHealth selectByScope(
            @Param("providerCode") String providerCode,
            @Param("platformCode") String platformCode,
            @Param("workspaceId") String workspaceId);

    /**
     * 查询某平台下所有健康记录。
     */
    @Select("""
            SELECT * FROM app.connector_health
            WHERE platform_code = #{platformCode}
            ORDER BY connector_provider_code
            """)
    List<ConnectorHealth> selectByPlatformCode(@Param("platformCode") String platformCode);

    /**
     * 查询全部健康记录。
     */
    @Select("""
            SELECT * FROM app.connector_health
            ORDER BY platform_code, connector_provider_code
            """)
    List<ConnectorHealth> selectAll();
}
