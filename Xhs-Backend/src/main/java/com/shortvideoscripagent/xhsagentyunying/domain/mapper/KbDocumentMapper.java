package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.KbDocument;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * KB document chunk mapper (kb.kb_document).
 * <p>
 * Basic CRUD comes from MyBatis-Plus {@link BaseMapper}; this mapper adds the
 * aggregate queries needed by the S3-11 KB management surface.
 * Every statement explicitly qualifies the {@code kb.} schema so it never
 * resolves against the {@code app} schema (the datasource currentSchema).
 * </p>
 * <p>
 * Note: in the aggregate queries {@code persona / tags} are {@code TEXT[]}.
 * A custom {@code @Select} result map does not inherit the entity TypeHandler,
 * so they are projected with {@code to_json(...)} to JSON text and parsed by
 * the service with Jackson. The {@link KbDocument} entity itself maps
 * {@code List<String>} via {@code @TableName(autoResultMap = true)}, which is
 * what BaseMapper reads and writes.
 * </p>
 */
@Mapper
public interface KbDocumentMapper extends BaseMapper<KbDocument> {

    /**
     * Aggregated document list grouped by doc_id (pagination happens after
     * the aggregation, so the total matches the number of documents).
     *
     * @param docType     document type filter, empty string means no filter
     * @param contentType content type filter, empty string means no filter
     * @param keyword     doc_id / title fuzzy filter, empty string means no filter
     * @param offset      row offset
     * @param limit       row limit
     */
    @Select("""
            SELECT doc_id,
                   MAX(title)        AS title,
                   MAX(doc_type)     AS doc_type,
                   MAX(content_type) AS content_type,
                   MAX(to_json(persona)::text) AS persona_json,
                   MAX(to_json(tags)::text)    AS tags_json,
                   COUNT(*)          AS chunk_count,
                   BOOL_OR(embedding IS NOT NULL) AS has_embedding,
                   MIN(created_at)   AS created_at,
                   MAX(updated_at)   AS updated_at,
                   MAX(workspace_id) AS workspace_id
            FROM kb.kb_document
            WHERE (#{docType} = '' OR doc_type = #{docType})
              AND (#{contentType} = '' OR content_type = #{contentType})
              AND (#{keyword} = ''
                   OR doc_id ILIKE CONCAT('%', #{keyword}, '%')
                   OR title ILIKE CONCAT('%', #{keyword}, '%'))
            GROUP BY doc_id
            ORDER BY MAX(updated_at) DESC, doc_id
            OFFSET #{offset} LIMIT #{limit}
            """)
    List<KbDocumentRow> selectAggregatedDocuments(
            @Param("docType") String docType,
            @Param("contentType") String contentType,
            @Param("keyword") String keyword,
            @Param("offset") long offset,
            @Param("limit") long limit
    );

    /**
     * Aggregated document count (same filters as
     * {@link #selectAggregatedDocuments}).
     */
    @Select("""
            SELECT COUNT(*) FROM (
                SELECT doc_id
                FROM kb.kb_document
                WHERE (#{docType} = '' OR doc_type = #{docType})
                  AND (#{contentType} = '' OR content_type = #{contentType})
                  AND (#{keyword} = ''
                       OR doc_id ILIKE CONCAT('%', #{keyword}, '%')
                       OR title ILIKE CONCAT('%', #{keyword}, '%'))
                GROUP BY doc_id
            ) t
            """)
    long countAggregatedDocuments(
            @Param("docType") String docType,
            @Param("contentType") String contentType,
            @Param("keyword") String keyword
    );

    /**
     * Aggregated row for a single document (exact doc_id match, so a title
     * keyword cannot pull in unrelated documents).
     */
    @Select("""
            SELECT doc_id,
                   MAX(title)        AS title,
                   MAX(doc_type)     AS doc_type,
                   MAX(content_type) AS content_type,
                   MAX(to_json(persona)::text) AS persona_json,
                   MAX(to_json(tags)::text)    AS tags_json,
                   COUNT(*)          AS chunk_count,
                   BOOL_OR(embedding IS NOT NULL) AS has_embedding,
                   MIN(created_at)   AS created_at,
                   MAX(updated_at)   AS updated_at,
                   MAX(workspace_id) AS workspace_id
            FROM kb.kb_document
            WHERE doc_id = #{docId}
            GROUP BY doc_id
            """)
    KbDocumentRow selectAggregatedDocumentByDocId(@Param("docId") String docId);

    /**
     * Chunk summaries for one document (never returns the embedding itself).
     */
    @Select("""
            SELECT chunk_type,
                   length(content)     AS content_length,
                   (embedding IS NOT NULL) AS has_embedding,
                   updated_at
            FROM kb.kb_document
            WHERE doc_id = #{docId}
            ORDER BY chunk_type
            """)
    List<KbChunkRow> selectChunkSummaries(@Param("docId") String docId);

    /**
     * Chunk count for one document.
     */
    @Select("SELECT COUNT(*) FROM kb.kb_document WHERE doc_id = #{docId}")
    long countChunksByDocId(@Param("docId") String docId);

    /**
     * Number of chunks of one document that carry an embedding.
     */
    @Select("SELECT COUNT(*) FROM kb.kb_document WHERE doc_id = #{docId} AND embedding IS NOT NULL")
    long countEmbeddedChunksByDocId(@Param("docId") String docId);

    /**
     * Latest index timestamp for one document.
     */
    @Select("SELECT MAX(updated_at) FROM kb.kb_document WHERE doc_id = #{docId}")
    OffsetDateTime selectMaxUpdatedAt(@Param("docId") String docId);

    /**
     * Keyword search over title / content with ILIKE.
     * <p>
     * The generated {@code tsv} column uses the 'simple' dictionary, which is
     * unreliable for Chinese text, so ILIKE is used instead. Rows are
     * returned per chunk; the service de-duplicates them by doc_id.
     * </p>
     */
    @Select("""
            <script>
            SELECT doc_id, title, doc_type, content_type, chunk_type, content
            FROM kb.kb_document
            WHERE (title ILIKE CONCAT('%', #{keyword}, '%')
                   OR content ILIKE CONCAT('%', #{keyword}, '%'))
            <if test="docTypes != null and docTypes.size() > 0">
              AND doc_type IN
              <foreach collection="docTypes" item="dt" open="(" separator="," close=")">#{dt}</foreach>
            </if>
            ORDER BY doc_id, chunk_type
            LIMIT #{limit}
            </script>
            """)
    List<KbSearchRow> searchByKeyword(
            @Param("keyword") String keyword,
            @Param("docTypes") List<String> docTypes,
            @Param("limit") long limit
    );

    /**
     * Deletes every chunk of one document; returns the number of deleted rows.
     */
    @Delete("DELETE FROM kb.kb_document WHERE doc_id = #{docId}")
    int deleteByDocId(@Param("docId") String docId);

    /**
     * Aggregate row projection.
     */
    class KbDocumentRow {
        private String docId;
        private String title;
        private String docType;
        private String contentType;
        private String personaJson;
        private String tagsJson;
        private long chunkCount;
        private Boolean hasEmbedding;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;
        private String workspaceId;

        public String getDocId() {
            return docId;
        }

        public void setDocId(String docId) {
            this.docId = docId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDocType() {
            return docType;
        }

        public void setDocType(String docType) {
            this.docType = docType;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public String getPersonaJson() {
            return personaJson;
        }

        public void setPersonaJson(String personaJson) {
            this.personaJson = personaJson;
        }

        public String getTagsJson() {
            return tagsJson;
        }

        public void setTagsJson(String tagsJson) {
            this.tagsJson = tagsJson;
        }

        public long getChunkCount() {
            return chunkCount;
        }

        public void setChunkCount(long chunkCount) {
            this.chunkCount = chunkCount;
        }

        public Boolean getHasEmbedding() {
            return hasEmbedding;
        }

        public void setHasEmbedding(Boolean hasEmbedding) {
            this.hasEmbedding = hasEmbedding;
        }

        public OffsetDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(OffsetDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public OffsetDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(OffsetDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }

        public String getWorkspaceId() {
            return workspaceId;
        }

        public void setWorkspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
        }
    }

    /**
     * Chunk summary projection.
     */
    class KbChunkRow {
        private String chunkType;
        private Integer contentLength;
        private Boolean hasEmbedding;
        private OffsetDateTime updatedAt;

        public String getChunkType() {
            return chunkType;
        }

        public void setChunkType(String chunkType) {
            this.chunkType = chunkType;
        }

        public Integer getContentLength() {
            return contentLength;
        }

        public void setContentLength(Integer contentLength) {
            this.contentLength = contentLength;
        }

        public Boolean getHasEmbedding() {
            return hasEmbedding;
        }

        public void setHasEmbedding(Boolean hasEmbedding) {
            this.hasEmbedding = hasEmbedding;
        }

        public OffsetDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(OffsetDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }
    }

    /**
     * Search result row projection.
     */
    class KbSearchRow {
        private String docId;
        private String title;
        private String docType;
        private String contentType;
        private String chunkType;
        private String content;

        public String getDocId() {
            return docId;
        }

        public void setDocId(String docId) {
            this.docId = docId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDocType() {
            return docType;
        }

        public void setDocType(String docType) {
            this.docType = docType;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public String getChunkType() {
            return chunkType;
        }

        public void setChunkType(String chunkType) {
            this.chunkType = chunkType;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
