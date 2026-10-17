package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ContentAssetMedia;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

/**
 * ContentAssetMedia Mapper（P0-3）。
 */
@Mapper
public interface ContentAssetMediaMapper extends BaseMapper<ContentAssetMedia> {

    /**
     * 查询某资产的所有媒体（按 sort_order 升序）。
     */
    @Select("""
            SELECT * FROM app.content_asset_media
            WHERE asset_id = #{assetId}
            ORDER BY sort_order ASC
            """)
    List<ContentAssetMedia> selectByAssetId(@Param("assetId") UUID assetId);

    /**
     * 查询某资产的封面图。
     */
    @Select("""
            SELECT * FROM app.content_asset_media
            WHERE asset_id = #{assetId} AND role = 'cover'
            ORDER BY sort_order ASC
            LIMIT 1
            """)
    ContentAssetMedia selectCoverByAssetId(@Param("assetId") UUID assetId);
}
