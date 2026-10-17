package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ContentAssetVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ContentAssetVersion Mapper（P0-3）。
 */
@Mapper
public interface ContentAssetVersionMapper extends BaseMapper<ContentAssetVersion> {

    /**
     * 查询某资产所有版本（按版本号降序）。
     */
    @Select("""
            SELECT * FROM app.content_asset_version
            WHERE asset_id = #{assetId}
            ORDER BY version_no DESC
            """)
    List<ContentAssetVersion> selectByAssetId(@Param("assetId") UUID assetId);

    /**
     * 查询指定版本的详情。
     */
    @Select("""
            SELECT * FROM app.content_asset_version
            WHERE asset_id = #{assetId} AND version_no = #{versionNo}
            LIMIT 1
            """)
    Optional<ContentAssetVersion> selectByAssetIdAndVersion(
            @Param("assetId") UUID assetId,
            @Param("versionNo") int versionNo
    );

    /**
     * 获取某资产的最大版本号。
     */
    @Select("""
            SELECT COALESCE(MAX(version_no), 0) FROM app.content_asset_version
            WHERE asset_id = #{assetId}
            """)
    int selectMaxVersionNo(@Param("assetId") UUID assetId);
}
