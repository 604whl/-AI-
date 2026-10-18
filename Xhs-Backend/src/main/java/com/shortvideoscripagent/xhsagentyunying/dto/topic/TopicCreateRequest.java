package com.shortvideoscripagent.xhsagentyunying.dto.topic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class TopicCreateRequest {

    @NotBlank(message = "title is required")
    @Size(max = 256, message = "title max 256 chars")
    private String title;

    @NotBlank(message = "source is required")
    @Size(max = 32, message = "source max 32 chars")
    private String source;

    private Integer heatScore = 0;

    private List<String> tags;

    private String targetPersona;

    private LocalDate scheduledDate;
}