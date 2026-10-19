package com.shortvideoscripagent.xhsagentyunying.dto.topic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicBulkImportResponse {

    private List<TopicDto> created;

    private List<SkippedItem> skipped;

    private int totalRequested;

    private int totalCreated;

    private int totalSkipped;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkippedItem {

        private String title;

        private String reason;
    }
}