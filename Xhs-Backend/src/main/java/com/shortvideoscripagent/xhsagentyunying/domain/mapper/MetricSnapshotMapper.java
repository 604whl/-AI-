package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.MetricSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

/**
 * S3-5 指标快照 Mapper。
 */
@Mapper
public interface MetricSnapshotMapper extends BaseMapper<MetricSnapshot> {

    /**
     * 按主键 + 工作区查询（工作区隔离是硬性要求）。
     */
    @Select("SELECT * FROM app.metric_snapshot WHERE id = #{id} AND workspace_id = #{workspaceId}")
    MetricSnapshot selectByIdAndWorkspace(@Param("id") UUID id, @Param("workspaceId") String workspaceId);

    /**
     * 查询某快照的后续修订链（按创建时间升序）。
     * <p>
     * 修订链的定义：所有 {@code revised_from_id} 指向该快照（或其后代）的快照，按 created_at 升序返回。
     */
    @Select("""
            WITH RECURSIVE chain(id) AS (
                SELECT id FROM app.metric_snapshot
                 WHERE id = #{id} AND workspace_id = #{workspaceId}
                UNION ALL
                SELECT s.id FROM app.metric_snapshot s
                  JOIN chain c ON s.revised_from_id = c.id
                 WHERE s.workspace_id = #{workspaceId}
            )
            SELECT s.* FROM app.metric_snapshot s
              JOIN chain c ON s.id = c.id
             WHERE s.workspace_id = #{workspaceId}
               AND s.id <> #{id}
             ORDER BY s.created_at ASC
            """)
    List<MetricSnapshot> selectRevisionChain(@Param("id") UUID id, @Param("workspaceId") String workspaceId);

    class TrendBucketRow {
        private String bucket;
        private java.math.BigDecimal impressions;
        private java.math.BigDecimal likes;
        private java.math.BigDecimal collects;
        private java.math.BigDecimal comments;
        private java.math.BigDecimal dmInquiries;

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public java.math.BigDecimal getImpressions() {
            return impressions;
        }

        public void setImpressions(java.math.BigDecimal impressions) {
            this.impressions = impressions;
        }

        public java.math.BigDecimal getLikes() {
            return likes;
        }

        public void setLikes(java.math.BigDecimal likes) {
            this.likes = likes;
        }

        public java.math.BigDecimal getCollects() {
            return collects;
        }

        public void setCollects(java.math.BigDecimal collects) {
            this.collects = collects;
        }

        public java.math.BigDecimal getComments() {
            return comments;
        }

        public void setComments(java.math.BigDecimal comments) {
            this.comments = comments;
        }

        public java.math.BigDecimal getDmInquiries() {
            return dmInquiries;
        }

        public void setDmInquiries(java.math.BigDecimal dmInquiries) {
            this.dmInquiries = dmInquiries;
        }
    }
}
