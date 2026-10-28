package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ExportLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * ExportLog Mapper（P0-5）。
 */
@Mapper
public interface ExportLogMapper extends BaseMapper<ExportLog> {

    /**
     * 分页查询 workspace 的导出记录（按时间倒序）。
     */
    @Select("""
            SELECT * FROM app.export_log
            WHERE workspace_id = #{workspaceId}
            ORDER BY created_at DESC
            """)
    IPage<ExportLog> selectPageByWorkspace(
            Page<ExportLog> page,
            @Param("workspaceId") String workspaceId
    );

    /**
     * 统计 workspace 的导出记录总数。
     */
    @Select("""
            SELECT COUNT(*) FROM app.export_log
            WHERE workspace_id = #{workspaceId}
            """)
    long countByWorkspace(@Param("workspaceId") String workspaceId);

    /**
     * 更新导出状态为 processing。
     */
    @Update("""
            UPDATE app.export_log
            SET status = 'processing'
            WHERE id = #{id} AND status = 'pending'
            """)
    int updateStatusToProcessing(@Param("id") UUID id);

    /**
     * 更新导出状态为 ready。
     */
    @Update("""
            UPDATE app.export_log
            SET status = 'ready',
                object_key = #{objectKey},
                file_size = #{fileSize},
                expires_at = #{expiresAt},
                finished_at = #{finishedAt}
            WHERE id = #{id}
            """)
    int updateStatusToReady(
            @Param("id") UUID id,
            @Param("objectKey") String objectKey,
            @Param("fileSize") long fileSize,
            @Param("expiresAt") OffsetDateTime expiresAt,
            @Param("finishedAt") OffsetDateTime finishedAt
    );

    /**
     * 更新导出状态为 failed。
     */
    @Update("""
            UPDATE app.export_log
            SET status = 'failed',
                error_message = #{errorMessage},
                finished_at = #{finishedAt}
            WHERE id = #{id}
            """)
    int updateStatusToFailed(
            @Param("id") UUID id,
            @Param("errorMessage") String errorMessage,
            @Param("finishedAt") OffsetDateTime finishedAt
    );

    /**
     * 查询 asset 关联的所有导出记录（按时间倒序）。
     */
    @Select("""
            SELECT * FROM app.export_log
            WHERE asset_id = #{assetId}
            ORDER BY created_at DESC
            """)
    List<ExportLog> selectByAssetId(@Param("assetId") UUID assetId);
}
