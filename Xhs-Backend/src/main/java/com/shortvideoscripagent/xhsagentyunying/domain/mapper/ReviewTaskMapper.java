package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ReviewTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface ReviewTaskMapper extends BaseMapper<ReviewTask> {

    /**
     * 查询审核人的待办队列（按创建时间倒序）。
     */
    @Select("""
            SELECT * FROM app.review_task
            WHERE workspace_id = #{workspaceId}
              AND reviewer_user_id = #{reviewerUserId}
              AND status = #{status}
            ORDER BY created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<ReviewTask> selectByReviewerAndStatus(
            @Param("workspaceId") String workspaceId,
            @Param("reviewerUserId") Long reviewerUserId,
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    /**
     * 查询审核人的所有待办（不限状态）。
     */
    @Select("""
            SELECT * FROM app.review_task
            WHERE workspace_id = #{workspaceId}
              AND reviewer_user_id = #{reviewerUserId}
              AND status IN ('pending','in_review')
            ORDER BY
                CASE WHEN is_high_risk THEN 0 ELSE 1 END,
                created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<ReviewTask> selectQueueByReviewer(
            @Param("workspaceId") String workspaceId,
            @Param("reviewerUserId") Long reviewerUserId,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    /**
     * 计数审核人待办数。
     */
    @Select("""
            SELECT COUNT(*) FROM app.review_task
            WHERE workspace_id = #{workspaceId}
              AND reviewer_user_id = #{reviewerUserId}
              AND status IN ('pending','in_review')
            """)
    long countQueueByReviewer(
            @Param("workspaceId") String workspaceId,
            @Param("reviewerUserId") Long reviewerUserId
    );

    /**
     * 查询 submitter 的提交记录。
     */
    @Select("""
            SELECT * FROM app.review_task
            WHERE workspace_id = #{workspaceId}
              AND submitted_by = #{submitterId}
            ORDER BY created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<ReviewTask> selectBySubmitter(
            @Param("workspaceId") String workspaceId,
            @Param("submitterId") Long submitterId,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    /**
     * 计数 submitter 提交总数。
     */
    @Select("""
            SELECT COUNT(*) FROM app.review_task
            WHERE workspace_id = #{workspaceId}
              AND submitted_by = #{submitterId}
            """)
    long countBySubmitter(
            @Param("workspaceId") String workspaceId,
            @Param("submitterId") Long submitterId
    );

    /**
     * 按 assetId + versionNo 查找最新审核任务。
     */
    @Select("""
            SELECT * FROM app.review_task
            WHERE asset_id = #{assetId}
              AND version_no = #{versionNo}
            ORDER BY created_at DESC
            LIMIT 1
            """)
    Optional<ReviewTask> selectByAssetAndVersion(
            @Param("assetId") UUID assetId,
            @Param("versionNo") int versionNo
    );

    /**
     * 软更新状态（乐观锁通过 updated_at）。
     */
    @Update("""
            UPDATE app.review_task
            SET status = #{status},
                comment = #{comment},
                decided_at = #{decidedAt},
                updated_at = #{updatedAt}
            WHERE id = #{id} AND status = #{expectedStatus}
            """)
    int updateStatusIfMatch(
            @Param("id") UUID id,
            @Param("expectedStatus") String expectedStatus,
            @Param("status") String newStatus,
            @Param("comment") String comment,
            @Param("decidedAt") java.time.OffsetDateTime decidedAt,
            @Param("updatedAt") java.time.OffsetDateTime updatedAt
    );

    /**
     * 指派审核人。
     */
    @Update("""
            UPDATE app.review_task
            SET reviewer_user_id = #{reviewerUserId},
                status = CASE WHEN status = 'pending' THEN 'in_review' ELSE status END,
                updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int assignReviewer(
            @Param("id") UUID id,
            @Param("reviewerUserId") Long reviewerUserId,
            @Param("updatedAt") java.time.OffsetDateTime updatedAt
    );
}
