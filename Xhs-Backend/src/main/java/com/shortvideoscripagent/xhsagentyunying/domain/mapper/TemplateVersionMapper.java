package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.TemplateVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * TemplateVersion Mapper（P0-4）。
 */
@Mapper
public interface TemplateVersionMapper extends BaseMapper<TemplateVersion> {

    /**
     * 查询某模板的最新版本号。
     */
    @Select("SELECT COALESCE(MAX(version_no), 0) FROM app.template_version WHERE template_id = #{templateId}")
    int selectMaxVersionNo(@Param("templateId") UUID templateId);

    /**
     * 查询某模板的最新版本记录。
     */
    @Select("SELECT * FROM app.template_version WHERE template_id = #{templateId} ORDER BY version_no DESC LIMIT 1")
    Optional<TemplateVersion> selectLatestByTemplateId(@Param("templateId") UUID templateId);

    /**
     * 查询指定版本的版本记录。
     */
    @Select("SELECT * FROM app.template_version WHERE template_id = #{templateId} AND version_no = #{versionNo}")
    Optional<TemplateVersion> selectByTemplateIdAndVersion(
            @Param("templateId") UUID templateId,
            @Param("versionNo") int versionNo
    );

    /**
     * 查询某模板所有版本（降序）。
     */
    @Select("SELECT * FROM app.template_version WHERE template_id = #{templateId} ORDER BY version_no DESC")
    List<TemplateVersion> selectByTemplateId(@Param("templateId") UUID templateId);
}
