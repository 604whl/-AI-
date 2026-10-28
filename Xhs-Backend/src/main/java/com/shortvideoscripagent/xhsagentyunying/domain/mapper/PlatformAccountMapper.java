package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.PlatformAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface PlatformAccountMapper extends BaseMapper<PlatformAccount> {

    /**
     * 按工作区 + 平台列出账号（工作区隔离是硬性要求，所有查询都必须带上 workspace_id）。
     */
    @Select("SELECT * FROM app.platform_account WHERE workspace_id = #{workspaceId} "
            + "AND platform_code = #{platformCode} ORDER BY created_at DESC")
    List<PlatformAccount> selectByWorkspaceAndPlatform(@Param("workspaceId") String workspaceId,
                                                        @Param("platformCode") String platformCode);

    /**
     * 按工作区 + 平台 + 标签查询（唯一性校验）。
     */
    @Select("SELECT * FROM app.platform_account WHERE workspace_id = #{workspaceId} "
            + "AND platform_code = #{platformCode} AND account_label = #{accountLabel} LIMIT 1")
    PlatformAccount selectByWorkspacePlatformLabel(@Param("workspaceId") String workspaceId,
                                                   @Param("platformCode") String platformCode,
                                                   @Param("accountLabel") String accountLabel);

    /**
     * 按主键 + 工作区查询（所有权校验：不属于本 workspace 一律视为未找到）。
     */
    @Select("SELECT * FROM app.platform_account WHERE id = #{id} AND workspace_id = #{workspaceId}")
    PlatformAccount selectByIdAndWorkspace(@Param("id") UUID id, @Param("workspaceId") String workspaceId);
}
