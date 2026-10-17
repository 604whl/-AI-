package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.DataRetentionPolicy;
import org.apache.ibatis.annotations.Mapper;

import java.util.UUID;

/**
 * DataRetentionPolicy Mapper（P0-9）。
 */
@Mapper
public interface DataRetentionPolicyMapper extends BaseMapper<DataRetentionPolicy> {

    /**
     * 查询 workspace 的保留策略（不存在则返回 null）。
     */
    default DataRetentionPolicy findByWorkspaceId(String workspaceId) {
        return selectById(workspaceId);
    }

    /**
     * 插入或更新保留策略。
     */
    default boolean insertOrUpdate(DataRetentionPolicy policy) {
        if (policy.getWorkspaceId() == null) {
            return false;
        }
        if (selectById(policy.getWorkspaceId()) == null) {
            return SqlHelper.retBool(insert(policy));
        }
        return SqlHelper.retBool(updateById(policy));
    }
}
