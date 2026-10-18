package com.shortvideoscripagent.xhsagentyunying.dto.topic;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class TopicUpdateRequest {

    private String status;

    private LocalDate scheduledDate;

    private List<String> tags;
}