package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ContentAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * ContentAsset Mapper（P0-3）。
 */
@Mapper
public interface ContentAssetMapper extends BaseMapper<ContentAsset> {

    /**
     * 分页查询当前 workspace 的资产列表，支持状态过滤。
     */
    @Select("""
            SELECT * FROM app.content_asset
            WHERE workspace_id = #{workspaceId}
              AND (#{status} IS NULL OR #{status} = '' OR status = #{status})
            ORDER BY updated_at DESC
            OFFSET #{offset} LIMIT #{limit}
            """)
    IPage<ContentAsset> selectPageByWorkspace(
            Page<ContentAsset> page,
            @Param("workspaceId") java.util.UUID workspaceId,
            @Param("status") String status,
            @Param("offset") long offset,
            @Param("limit") long limit
    );

    /**
     * 统计当前 workspace 满足条件的资产数量。
     */
    @Select("""
            SELECT COUNT(*) FROM app.content_asset
            WHERE workspace_id = #{workspaceId}
              AND (#{status} IS NULL OR #{status} = '' OR status = #{status})
            """)
    long countByWorkspace(@Param("workspaceId") java.util.UUID workspaceId, @Param("status") String status);
}
