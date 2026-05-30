package com.shortvideoscripagent.xhsagentyunying.ai.rag;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class RagContextBuilder {

    public String build(List<RagChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        return IntStream.range(0, chunks.size())
                .mapToObj(i -> formatChunk(i + 1, chunks.get(i)))
                .collect(Collectors.joining("\n\n"));
    }

    private String formatChunk(int index, RagChunk chunk) {
        return "[" + index + "] " + chunk.docType()
                + " | docId=" + chunk.docId()
                + "\n" + chunk.content();
    }
}
