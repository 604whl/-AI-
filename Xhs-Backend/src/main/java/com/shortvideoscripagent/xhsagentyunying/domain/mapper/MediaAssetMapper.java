package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.MediaAsset;
import org.apache.ibatis.annotations.*;

@Mapper
public interface MediaAssetMapper extends BaseMapper<MediaAsset> {
    @Select("SELECT * FROM app.media_asset WHERE workspace_id = #{wsId} ORDER BY created_at DESC LIMIT 50")
    java.util.List<MediaAsset> selectRecentByWorkspace(@Param("wsId") String wsId);

    @Select("SELECT * FROM app.media_asset WHERE asset_id = #{assetId} ORDER BY created_at DESC")
    java.util.List<MediaAsset> selectByAssetId(@Param("assetId") java.util.UUID assetId);
}