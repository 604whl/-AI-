package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.CompetitorNote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

/**
 * S3-10 竞品笔记 Mapper。
 */
@Mapper
public interface CompetitorNoteMapper extends BaseMapper<CompetitorNote> {

    /**
     * 按 id + workspace 查询。
     */
    @Select("""
            SELECT * FROM app.competitor_note
            WHERE id = #{id} AND workspace_id = #{workspaceId}
            LIMIT 1
            """)
    CompetitorNote selectByIdAndWorkspace(
            @Param("id") UUID id,
            @Param("workspaceId") String workspaceId);

    /**
     * 列出某竞品账号下的笔记（按采集时间倒序）。
     */
    @Select("""
            SELECT * FROM app.competitor_note
            WHERE competitor_account_id = CAST(#{accountId} AS uuid)
              AND workspace_id = CAST(#{workspaceId} AS VARCHAR)
            ORDER BY captured_at DESC, created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<CompetitorNote> listByAccount(
            @Param("accountId") UUID accountId,
            @Param("workspaceId") String workspaceId,
            @Param("offset") long offset,
            @Param("limit") long limit);

    /**
     * 统计某竞品账号下的笔记数。
     */
    @Select("""
            SELECT COUNT(*) FROM app.competitor_note
            WHERE competitor_account_id = CAST(#{accountId} AS uuid)
              AND workspace_id = CAST(#{workspaceId} AS VARCHAR)
            """)
    long countByAccount(
            @Param("accountId") UUID accountId,
            @Param("workspaceId") String workspaceId);

    /**
     * 批量取多个竞品账号的笔记数（用于列表页 noteCount）。
     */
    @Select("""
            <script>
            SELECT competitor_account_id, COUNT(*) AS note_count
            FROM app.competitor_note
            WHERE workspace_id = CAST(#{workspaceId} AS VARCHAR)
              AND competitor_account_id IN
              <foreach collection="accountIds" item="aid" open="(" separator="," close=")">
                CAST(#{aid} AS uuid)
              </foreach>
            GROUP BY competitor_account_id
            </script>
            """)
    List<NoteCountRow> countByAccounts(
            @Param("workspaceId") String workspaceId,
            @Param("accountIds") List<UUID> accountIds);

    /**
     * 笔记数聚合行。
     */
    class NoteCountRow {
        private UUID competitorAccountId;
        private Long noteCount;

        public UUID getCompetitorAccountId() {
            return competitorAccountId;
        }

        public void setCompetitorAccountId(UUID competitorAccountId) {
            this.competitorAccountId = competitorAccountId;
        }

        public Long getNoteCount() {
            return noteCount;
        }

        public void setNoteCount(Long noteCount) {
            this.noteCount = noteCount;
        }
    }
}
