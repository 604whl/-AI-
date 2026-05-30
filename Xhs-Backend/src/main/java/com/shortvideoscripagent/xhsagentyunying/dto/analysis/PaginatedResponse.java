package com.shortvideoscripagent.xhsagentyunying.dto.analysis;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PaginatedResponse<T> {

    private List<T> items;
    private long total;
    private int page;
    private int size;
}
