package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.WorkspaceDestruction;
import org.apache.ibatis.annotations.Mapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * WorkspaceDestruction Mapper（P0-9）。
 */
@Mapper
public interface WorkspaceDestructionMapper extends BaseMapper<WorkspaceDestruction> {

    /**
     * 查询 workspace 的待执行销毁记录。
     */
    default WorkspaceDestruction findPendingOrConfirmedByWorkspace(String workspaceId) {
        return selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WorkspaceDestruction>()
                        .eq(WorkspaceDestruction::getWorkspaceId, workspaceId)
                        .in(WorkspaceDestruction::getStatus,
                                WorkspaceDestruction.STATUS_PENDING,
                                WorkspaceDestruction.STATUS_CONFIRMED)
                        .orderByDesc(WorkspaceDestruction::getCreatedAt)
                        .last("LIMIT 1")
        );
    }

    /**
     * 扫描所有已到执行时间的工作区销毁记录（定时任务调用）。
     */
    default List<WorkspaceDestruction> findReadyToExecute(OffsetDateTime now) {
        return selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WorkspaceDestruction>()
                        .eq(WorkspaceDestruction::getStatus, WorkspaceDestruction.STATUS_CONFIRMED)
                        .le(WorkspaceDestruction::getExecuteAt, now)
        );
    }
}
