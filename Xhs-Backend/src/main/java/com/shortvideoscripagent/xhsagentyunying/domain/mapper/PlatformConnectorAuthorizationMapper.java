package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.PlatformConnectorAuthorization;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 平台 × 连接器 授权 Mapper。
 */
@Mapper
public interface PlatformConnectorAuthorizationMapper extends BaseMapper<PlatformConnectorAuthorization> {

    /**
     * 查询指定平台 + 连接器的授权记录。
     */
    @Select("""
            SELECT * FROM app.platform_connector_authorization
            WHERE platform_code = #{platformCode}
              AND connector_provider_code = #{providerCode}
            LIMIT 1
            """)
    PlatformConnectorAuthorization selectByPlatformAndProvider(
            @Param("platformCode") String platformCode,
            @Param("providerCode") String providerCode);

    /**
     * 查询某平台下所有已授权的连接器。
     */
    @Select("""
            SELECT * FROM app.platform_connector_authorization
            WHERE platform_code = #{platformCode}
              AND authorized = TRUE
            ORDER BY connector_provider_code
            """)
    List<PlatformConnectorAuthorization> selectAuthorizedByPlatform(
            @Param("platformCode") String platformCode);

    /**
     * 查询全部授权记录。
     */
    @Select("""
            SELECT * FROM app.platform_connector_authorization
            ORDER BY platform_code, connector_provider_code
            """)
    List<PlatformConnectorAuthorization> selectAllAuthorizations();
}
