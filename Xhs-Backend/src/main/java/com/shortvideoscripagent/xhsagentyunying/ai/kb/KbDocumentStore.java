package com.shortvideoscripagent.xhsagentyunying.ai.kb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class KbDocumentStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public void deleteByDocId(String docId) {
        jdbcTemplate.update("DELETE FROM kb.kb_document WHERE doc_id = ?", docId);
    }

    public void upsertChunk(KbChunkDraft chunk, float[] embedding) {
        String metadataJson = toJson(chunk.metadata());
        String embeddingLiteral = toPgVectorLiteral(embedding);

        jdbcTemplate.update("""
                INSERT INTO kb.kb_document (
                    doc_id, doc_type, content_type, persona, tags, title,
                    chunk_type, content, metadata, embedding, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::vector, NOW())
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
                """,
                chunk.docId(),
                chunk.docType(),
                chunk.contentType(),
                toTextArray(chunk.persona()),
                toTextArray(chunk.tags()),
                chunk.title(),
                chunk.chunkType(),
                chunk.content(),
                metadataJson,
                embeddingLiteral
        );
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("kb metadata json error", ex);
        }
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

    private Array toTextArray(List<String> values) {
        String[] array = values == null ? new String[0] : values.toArray(String[]::new);
        return jdbcTemplate.execute((Connection connection) ->
                connection.createArrayOf("text", array));
    }
}
