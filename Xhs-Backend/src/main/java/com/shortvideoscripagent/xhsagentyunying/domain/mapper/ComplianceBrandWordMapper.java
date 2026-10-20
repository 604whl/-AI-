package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ComplianceBrandWord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

/**
 * ComplianceBrandWord Mapper（S2-3）。
 */
@Mapper
public interface ComplianceBrandWordMapper extends BaseMapper<ComplianceBrandWord> {

    @Select("SELECT * FROM app.compliance_brand_word WHERE workspace_id = #{workspaceId}")
    List<ComplianceBrandWord> selectByWorkspace(@Param("workspaceId") UUID workspaceId);
}