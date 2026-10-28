package com.shortvideoscripagent.xhsagentyunying.dto.competitor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * S3-10 竞品账号列表项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorAccountListItem {

    private String id;

    private String platformCode;

    private String accountName;

    private String accountUrl;

    private Long followerCount;

    private String category;

    private List<String> tags;

    /** 该账号下的竞品笔记数 */
    private Long noteCount;

    /** active / archived */
    private String status;

    /** 最后更新时间（接口必须暴露） */
    private OffsetDateTime updatedAt;

    /** 创建时间 */
    private OffsetDateTime createdAt;
}
