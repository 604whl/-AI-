package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.RetrospectiveFeedback;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.UUID;

/**
 * S3-8 复盘回写审批 Mapper。
 * <p>
 * 所有查询都必须带 {@code workspace_id} 过滤（工作区隔离是硬性要求）。
 * </p>
 */
@Mapper
public interface RetrospectiveFeedbackMapper extends BaseMapper<RetrospectiveFeedback> {

    /**
     * 按主键 + 工作区查询。
     */
    @Select("SELECT * FROM app.retrospective_feedback WHERE id = #{id} AND workspace_id = #{workspaceId}")
    RetrospectiveFeedback selectByIdAndWorkspace(@Param("id") UUID id, @Param("workspaceId") String workspaceId);

    /**
     * 按幂等键查询（同一工作区内幂等）。
     */
    @Select("""
            SELECT * FROM app.retrospective_feedback
            WHERE workspace_id = #{workspaceId}
              AND idempotency_key = #{idempotencyKey}
            LIMIT 1
            """)
    RetrospectiveFeedback selectByIdempotencyKey(@Param("workspaceId") String workspaceId,
                                                   @Param("idempotencyKey") String idempotencyKey);

    /**
     * 列出某报告下的全部回写记录。
     */
    @Select("""
            SELECT * FROM app.retrospective_feedback
            WHERE workspace_id = #{workspaceId}
              AND report_id = #{reportId}
            ORDER BY created_at DESC
            """)
    List<RetrospectiveFeedback> listByReport(@Param("workspaceId") String workspaceId,
                                             @Param("reportId") UUID reportId);

    /**
     * 列出工作区内某目标（模板 / 知识库文档）的回写记录。
     */
    @Select("""
            SELECT * FROM app.retrospective_feedback
            WHERE workspace_id = #{workspaceId}
              AND target_type = #{targetType}
              AND target_id = #{targetId}
            ORDER BY created_at DESC
            """)
    List<RetrospectiveFeedback> listByTarget(@Param("workspaceId") String workspaceId,
                                              @Param("targetType") String targetType,
                                              @Param("targetId") String targetId);

    /**
     * CAS 审批：只有 PENDING 状态才能被改为终态，避免并发重复审批。
     *
     * @return 受影响行数（0 表示已被其他人处理）
     */
    @Update("""
            UPDATE app.retrospective_feedback
               SET status = #{status},
                   reviewed_by = #{reviewedBy},
                   reviewed_at = NOW(),
                   review_comment = #{reviewComment},
                   applied_at = #{appliedAt}
             WHERE id = #{id}
               AND workspace_id = #{workspaceId}
               AND status = 'PENDING'
            """)
    int review(@Param("id") UUID id,
               @Param("workspaceId") String workspaceId,
               @Param("status") String status,
               @Param("reviewedBy") Long reviewedBy,
               @Param("reviewComment") String reviewComment,
               @Param("appliedAt") java.time.OffsetDateTime appliedAt);
}
