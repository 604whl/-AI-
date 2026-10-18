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

    @Select("SELECT tag FROM app.asset_tag WHERE asset_uuid = #{assetUuid} ORDER BY tag ASC")
    List<String> selectTagsByAssetUuid(@Param("assetUuid") String assetUuid);

    @Select("SELECT asset_uuid FROM app.asset_tag WHERE tag = #{tag}")
    List<String> selectAssetUuidsByTag(@Param("tag") String tag);

    @Select({"SELECT DISTINCT asset_uuid FROM app.asset_tag",
             "WHERE tag IN (",
             "<foreach collection='tags' item='t' separator=','>",
             "#{t}",
             "</foreach>",
             ")"})
    List<String> selectAssetUuidsByAnyTag(@Param("tags") List<String> tags);

    @Delete("DELETE FROM app.asset_tag WHERE asset_uuid = #{assetUuid}")
    int deleteByAssetUuid(@Param("assetUuid") String assetUuid);
}