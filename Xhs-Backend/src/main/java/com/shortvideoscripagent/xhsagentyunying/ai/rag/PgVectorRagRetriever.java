package com.shortvideoscripagent.xhsagentyunying.ai.rag;

import com.shortvideoscripagent.xhsagentyunying.ai.kb.KbEmbeddingService;
import com.shortvideoscripagent.xhsagentyunying.config.AppAiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PgVectorRagRetriever implements RagRetriever {

    private final JdbcTemplate jdbcTemplate;
    private final AppAiProperties appProperties;
    private final KbEmbeddingService kbEmbeddingService;

    @Override
    public List<RagChunk> retrieve(RagQuery query) {
        if (!appProperties.getRag().isEnabled()) {
            return Collections.emptyList();
        }
        if (query.queryText() == null || query.queryText().isBlank()) {
            return Collections.emptyList();
        }

        try {
            List<float[]> embeddings = kbEmbeddingService.embedBatch(List.of(query.queryText()));
            if (embeddings.isEmpty()) {
                return Collections.emptyList();
            }
            String vectorLiteral = toPgVectorLiteral(embeddings.getFirst());
            int topK = query.topK() > 0 ? query.topK() : appProperties.getRag().getTopK();

            StringBuilder sql = new StringBuilder("""
                    SELECT doc_id, doc_type, content, metadata,
                           1 - (embedding <=> ?::vector) AS score
                    FROM kb.kb_document
                    WHERE embedding IS NOT NULL
                    """);
            List<Object> params = new ArrayList<>();
            params.add(vectorLiteral);

            if (query.docTypes() != null && !query.docTypes().isEmpty()) {
                sql.append(" AND doc_type IN (");
                sql.append(String.join(",", Collections.nCopies(query.docTypes().size(), "?")));
                sql.append(")");
                params.addAll(query.docTypes());
            }
            if (query.contentType() != null && !query.contentType().isBlank()) {
                sql.append(" AND content_type = ?");
                params.add(query.contentType());
            }

            sql.append(" ORDER BY embedding <=> ?::vector LIMIT ?");
            params.add(vectorLiteral);
            params.add(topK);

            return jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) -> new RagChunk(
                    rs.getString("doc_id"),
                    rs.getString("doc_type"),
                    rs.getString("content"),
                    rs.getDouble("score"),
                    parseMetadata(rs.getString("metadata"))
            ));
        } catch (Exception ex) {
            log.warn("RAG retrieve failed: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private static String toPgVectorLiteral(float[] embedding) {
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
