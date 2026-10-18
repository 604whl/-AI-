package com.shortvideoscripagent.xhsagentyunying.freeze.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.freeze.domain.entity.PlatformConnectorAuthorization;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

/**
 * M0-5: platform connector authorization Mapper.
 */
@Mapper
public interface PlatformConnectorAuthorizationMapper extends BaseMapper<PlatformConnectorAuthorization> {

    /**
     * Query valid authorization for platform + connector.
     * Valid = authorized = TRUE AND (expires_at IS NULL OR expires_at > now())
     *
     * @param platformCode platform code, e.g. XIAOHONGSHU
     * @param connectorProviderCode connector implementation code, e.g. XHS_BROWSER_SIDEBAR
     * @return valid authorization record (null if not exists)
     */
    @Select("""
            SELECT * FROM app.platform_connector_authorization
            WHERE platform_code = #{platformCode}
              AND connector_provider_code = #{connectorProviderCode}
              AND authorized = TRUE
              AND (expires_at IS NULL OR expires_at > now())
            LIMIT 1
            """)
    Optional<PlatformConnectorAuthorization> findValidAuthorization(
            @Param("platformCode") String platformCode,
            @Param("connectorProviderCode") String connectorProviderCode
    );
}