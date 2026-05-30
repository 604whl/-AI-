package com.shortvideoscripagent.xhsagentyunying.ai.rag;

import java.util.List;

public interface RagRetriever {

    List<RagChunk> retrieve(RagQuery query);
}
