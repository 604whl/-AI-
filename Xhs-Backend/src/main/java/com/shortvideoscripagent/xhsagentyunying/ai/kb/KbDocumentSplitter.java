package com.shortvideoscripagent.xhsagentyunying.ai.kb;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 使用 Spring AI {@link TokenTextSplitter} 做基于 token 的智能切片（句界优化 + 可配置 overlap）。
 */
@Component
@RequiredArgsConstructor
public class KbDocumentSplitter {

    private final TokenTextSplitter tokenTextSplitter;

    public List<KbChunkDraft> split(KbSourceDescriptor source) {
        if (source.body() == null || source.body().isBlank()) {
            return List.of();
        }

        Map<String, Object> baseMetadata = new HashMap<>(source.extraMetadata());
        baseMetadata.put("doc_id", source.docId());
        baseMetadata.put("doc_type", source.docType());
        if (source.contentType() != null) {
            baseMetadata.put("content_type", source.contentType());
        }
        if (source.title() != null) {
            baseMetadata.put("title", source.title());
        }

        Document root = Document.builder()
                .text(source.body())
                .metadata(baseMetadata)
                .build();

        List<Document> chunks = tokenTextSplitter.apply(List.of(root));
        List<KbChunkDraft> drafts = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
            metadata.put("chunk_index", i);
            metadata.put("chunk_total", chunks.size());

            drafts.add(new KbChunkDraft(
                    source.docId(),
                    source.docType(),
                    source.contentType(),
                    source.title(),
                    source.tags(),
                    source.persona(),
                    "part_" + i,
                    chunk.getText(),
                    metadata
            ));
        }
        return drafts;
    }
}
