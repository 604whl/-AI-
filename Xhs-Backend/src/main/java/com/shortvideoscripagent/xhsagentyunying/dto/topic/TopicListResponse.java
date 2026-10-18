package com.shortvideoscripagent.xhsagentyunying.dto.topic;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TopicListResponse {

    private List<TopicDto> items;
    private long total;
    private int page;
    private int size;
}