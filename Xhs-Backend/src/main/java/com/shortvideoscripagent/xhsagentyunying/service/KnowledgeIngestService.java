package com.shortvideoscripagent.xhsagentyunying.service;

import com.shortvideoscripagent.xhsagentyunying.ai.kb.KbChunkDraft;
import com.shortvideoscripagent.xhsagentyunying.ai.kb.KbDocumentSplitter;
import com.shortvideoscripagent.xhsagentyunying.ai.kb.KbDocumentStore;
import com.shortvideoscripagent.xhsagentyunying.ai.kb.KbEmbeddingService;
import com.shortvideoscripagent.xhsagentyunying.ai.kb.KbSourceDescriptor;
import com.shortvideoscripagent.xhsagentyunying.ai.kb.KbSourceLoader;
import com.shortvideoscripagent.xhsagentyunying.config.AppKbProperties;
import com.shortvideoscripagent.xhsagentyunying.dto.kb.KbIngestResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeIngestService {

    private final KbSourceLoader sourceLoader;
    private final KbDocumentSplitter documentSplitter;
    private final KbEmbeddingService embeddingService;
    private final KbDocumentStore documentStore;
    private final AppKbProperties kbProperties;

    /**
     * 从配置的源目录加载文档 → Spring AI 智能切片 →（可选）Embedding → 写入 kb.kb_document。
     */
    public KbIngestResult reindexFromSourceDir() throws IOException {
        List<KbSourceDescriptor> sources = sourceLoader.loadAll();
        int filesProcessed = 0;
        int chunksCreated = 0;
        int chunksPersisted = 0;
        List<String> errors = new ArrayList<>();

        for (KbSourceDescriptor source : sources) {
            try {
                List<KbChunkDraft> chunks = documentSplitter.split(source);
                if (chunks.isEmpty()) {
                    errors.add(source.docId() + ": empty body");
                    continue;
                }
                documentStore.deleteByDocId(source.docId());
                persistChunks(chunks);
                filesProcessed++;
                chunksCreated += chunks.size();
                chunksPersisted += chunks.size();
            } catch (Exception ex) {
                log.warn("KB ingest failed for {}: {}", source.docId(), ex.getMessage());
                errors.add(source.docId() + ": " + ex.getMessage());
            }
        }

        return new KbIngestResult(filesProcessed, chunksCreated, chunksPersisted, errors);
    }

    /**
     * 仅切片预览，不写库、不调用 Embedding（便于后期调参）。
     */
    public List<KbChunkDraft> previewSplit(String docId) throws IOException {
        return sourceLoader.loadAll().stream()
                .filter(s -> s.docId().equals(docId))
                .findFirst()
                .map(documentSplitter::split)
                .orElse(List.of());
    }

    private void persistChunks(List<KbChunkDraft> chunks) {
        if (kbProperties.getIngest().isEmbedOnIngest()) {
            List<String> texts = chunks.stream().map(KbChunkDraft::content).toList();
            List<float[]> vectors = embeddingService.embedInBatches(
                    texts,
                    kbProperties.getIngest().getEmbeddingBatchSize()
            );
            for (int i = 0; i < chunks.size(); i++) {
                documentStore.upsertChunk(chunks.get(i), vectors.get(i));
            }
        } else {
            for (KbChunkDraft chunk : chunks) {
                documentStore.upsertChunk(chunk, null);
            }
        }
    }
}
