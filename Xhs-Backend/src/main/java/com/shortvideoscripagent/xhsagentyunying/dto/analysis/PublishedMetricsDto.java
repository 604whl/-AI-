package com.shortvideoscripagent.xhsagentyunying.dto.analysis;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class PublishedMetricsDto {

    @Size(max = 500)
    private String noteUrl;

    @Min(0)
    private Integer impressions;

    @Min(0)
    private Integer likes;

    @Min(0)
    private Integer collects;

    @Min(0)
    private Integer comments;

    @Min(0)
    private Integer dmInquiries;

    private OffsetDateTime publishedAt;
}
