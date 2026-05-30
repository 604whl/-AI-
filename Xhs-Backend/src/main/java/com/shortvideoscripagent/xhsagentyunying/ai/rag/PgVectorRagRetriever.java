package com.shortvideoscripagent.xhsagentyunying.ai.rag;

import com.shortvideoscripagent.xhsagentyunying.config.AppAiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * PostgreSQL + pgvector 检索实现（P2 启用 app.rag.enabled）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PgVectorRagRetriever implements RagRetriever {

    private final JdbcTemplate jdbcTemplate;
    private final AppAiProperties appProperties;

    @Override
    public List<RagChunk> retrieve(RagQuery query) {
        if (!appProperties.getRag().isEnabled()) {
            return Collections.emptyList();
        }
        // TODO: 1) Embedding 2) cosine 检索 kb.kb_document
        log.debug("RAG retrieve skipped or not implemented, query length={}", query.queryText().length());
        return Collections.emptyList();
    }
}
