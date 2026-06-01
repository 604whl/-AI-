package com.shortvideoscripagent.xhsagentyunying.dto.kb;

import java.util.List;

public record KbIngestResult(
        int filesProcessed,
        int chunksCreated,
        int chunksPersisted,
        List<String> errors
) {
}
