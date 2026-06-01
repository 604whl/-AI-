package com.shortvideoscripagent.xhsagentyunying.ai.kb;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class KbEmbeddingService {

    private static final int CODE_KB_EMBEDDING_UNAVAILABLE = 50010;

    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;

    public List<float[]> embedBatch(List<String> texts) {
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {
            throw new BusinessException(CODE_KB_EMBEDDING_UNAVAILABLE, "kb_embedding_unavailable");
        }
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        return embeddingModel.embed(texts);
    }

    public List<float[]> embedInBatches(List<String> texts, int batchSize) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        int size = Math.max(1, batchSize);
        List<float[]> all = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += size) {
            int end = Math.min(i + size, texts.size());
            List<String> batch = texts.subList(i, end);
            all.addAll(embedBatch(batch));
        }
        return all;
    }
}
