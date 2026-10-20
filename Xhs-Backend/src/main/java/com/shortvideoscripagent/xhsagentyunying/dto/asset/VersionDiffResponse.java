package com.shortvideoscripagent.xhsagentyunying.dto.asset;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class VersionDiffResponse {
    private String assetId;
    private Integer versionA;
    private Integer versionB;
    private List<DiffChunk> chunks;
    private DiffStats stats;
    private List<String> changeSummary;

    @Data
    @Builder
    public static class DiffStats {
        private Long addedLines;
        private Long removedLines;
        private Long unchangedLines;
    }

    public record DiffChunk(String type, String content) {}
}