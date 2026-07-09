package com.shortvideoscripagent.xhsagentyunying.ai.kb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class KbDocumentStore {

    private static final String UPSERT_SQL = """
            INSERT INTO kb.kb_document (
                doc_id, doc_type, content_type, persona, tags, title,
                chunk_type, content, metadata, embedding, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
            ON CONFLICT (doc_id, chunk_type) DO UPDATE SET
                doc_type = EXCLUDED.doc_type,
                content_type = EXCLUDED.content_type,
                persona = EXCLUDED.persona,
                tags = EXCLUDED.tags,
                title = EXCLUDED.title,
                content = EXCLUDED.content,
                metadata = EXCLUDED.metadata,
                embedding = EXCLUDED.embedding,
                updated_at = NOW()
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public void deleteByDocId(String docId) {
        jdbcTemplate.update("DELETE FROM kb.kb_document WHERE doc_id = ?", docId);
    }

    public void upsertChunk(KbChunkDraft chunk, float[] embedding) {
        String metadataJson = toJson(chunk.metadata());
        String embeddingLiteral = toPgVectorLiteral(embedding);
        String[] persona = toArray(chunk.persona());
        String[] tags = toArray(chunk.tags());

        jdbcTemplate.update(UPSERT_SQL, (PreparedStatement ps) -> {
            ps.setString(1, chunk.docId());
            ps.setString(2, chunk.docType());
            ps.setString(3, chunk.contentType());
            ps.setArray(4, ps.getConnection().createArrayOf("text", persona));
            ps.setArray(5, ps.getConnection().createArrayOf("text", tags));
            ps.setString(6, chunk.title());
            ps.setString(7, chunk.chunkType());
            ps.setString(8, chunk.content());
            ps.setObject(9, metadataJson, Types.OTHER);
            if (embeddingLiteral == null) {
                ps.setNull(10, Types.OTHER);
            } else {
                ps.setObject(10, embeddingLiteral, Types.OTHER);
            }
        });
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("kb metadata json error", ex);
        }
    }

    private static String[] toArray(List<String> values) {
        return values == null ? new String[0] : values.toArray(String[]::new);
    }

    private static String toPgVectorLiteral(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder(embedding.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
