package com.shortvideoscripagent.xhsagentyunying.dto.topic;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class TopicBulkImportRequest {

    @Size(max = 32, message = "source max 32 chars")
    private String defaultSource = "ai_search";

    private String defaultTargetPersona;

    @NotEmpty(message = "items cannot be empty")
    @Valid
    private List<TopicCreateItem> items;

    @Data
    public static class TopicCreateItem {

        @Size(max = 256, message = "title max 256 chars")
        private String title;

        @Size(max = 32, message = "source max 32 chars")
        private String source;

        private Integer heatScore;

        private List<String> tags;

        private String targetPersona;

        private java.time.LocalDate scheduledDate;
    }
}