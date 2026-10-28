package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ConnectorProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 连接器提供方 Mapper。
 */
@Mapper
public interface ConnectorProviderMapper extends BaseMapper<ConnectorProvider> {

    /**
     * 按平台代码查询所有连接器提供方。
     */
    @Select("SELECT * FROM app.connector_provider WHERE platform_code = #{platformCode} ORDER BY code")
    List<ConnectorProvider> selectByPlatformCode(@Param("platformCode") String platformCode);
}
