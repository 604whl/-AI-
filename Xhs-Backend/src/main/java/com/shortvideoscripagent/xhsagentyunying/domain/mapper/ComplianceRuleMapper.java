package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ComplianceRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * ComplianceRule Mapper（S2-3: DB-backed 规则查询）。
 */
@Mapper
public interface ComplianceRuleMapper extends BaseMapper<ComplianceRule> {

    @Select("SELECT * FROM app.compliance_rule WHERE enabled = true")
    List<ComplianceRule> selectAllEnabled();
}