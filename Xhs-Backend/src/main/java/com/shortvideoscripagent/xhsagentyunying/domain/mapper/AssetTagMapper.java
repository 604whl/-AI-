package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.AssetTag;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AssetTagMapper extends BaseMapper<AssetTag> {

    @Select("SELECT tag FROM app.asset_tag WHERE asset_id = #{assetId} ORDER BY tag ASC")
    List<String> selectTagsByAssetId(@Param("assetId") Long assetId);

    @Select("SELECT asset_id FROM app.asset_tag WHERE tag = #{tag}")
    List<Long> selectAssetIdsByTag(@Param("tag") String tag);

    @Select({"SELECT DISTINCT asset_id FROM app.asset_tag",
             "WHERE tag IN (",
             "<foreach collection='tags' item='t' separator=','>",
             "#{t}",
             "</foreach>",
             ")"})
    List<Long> selectAssetIdsByAnyTag(@Param("tags") List<String> tags);

    @Delete("DELETE FROM app.asset_tag WHERE asset_id = #{assetId}")
    int deleteByAssetId(@Param("assetId") Long assetId);
}