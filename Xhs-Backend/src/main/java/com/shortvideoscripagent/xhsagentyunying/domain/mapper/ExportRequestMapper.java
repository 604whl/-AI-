package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ExportRequest;
import org.apache.ibatis.annotations.Mapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * ExportRequest Mapper（P0-9）。
 */
@Mapper
public interface ExportRequestMapper extends BaseMapper<ExportRequest> {

    /**
     * 查询用户的所有导出请求（按时间倒序）。
     */
    default List<ExportRequest> findByUserIdOrderByCreatedAtDesc(Long userId) {
        return selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExportRequest>()
                        .eq(ExportRequest::getUserId, userId)
                        .orderByDesc(ExportRequest::getCreatedAt)
        );
    }

    /**
     * 查询过期的导出记录（用于定时清理）。
     */
    default List<ExportRequest> findExpired(OffsetDateTime now) {
        return selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExportRequest>()
                        .eq(ExportRequest::getStatus, ExportRequest.STATUS_READY)
                        .le(ExportRequest::getExpiresAt, now)
        );
    }

    /**
     * 查询正在处理的导出请求。
     */
    default List<ExportRequest> findProcessing() {
        return selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExportRequest>()
                        .in(ExportRequest::getStatus,
                                ExportRequest.STATUS_PENDING,
                                ExportRequest.STATUS_PROCESSING)
        );
    }

    /**
     * 按 workspaceId 查询。
     */
    default List<ExportRequest> findByWorkspaceId(String workspaceId) {
        return selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExportRequest>()
                        .eq(ExportRequest::getWorkspaceId, workspaceId)
                        .orderByDesc(ExportRequest::getCreatedAt)
        );
    }
}
