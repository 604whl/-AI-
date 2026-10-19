package com.shortvideoscripagent.xhsagentyunying.dto.hottopic;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for the /refresh endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HotTopicRefreshResponse {

    private int count;
    private String message;
}
