package com.shortvideoscripagent.xhsagentyunying.ai.kb;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 入库前的源文档描述（尚未切片）。
 */
public record KbSourceDescriptor(
        String docId,
        String docType,
        String contentType,
        String title,
        List<String> tags,
        List<String> persona,
        String body,
        Path sourcePath,
        Map<String, Object> extraMetadata
) {
}
