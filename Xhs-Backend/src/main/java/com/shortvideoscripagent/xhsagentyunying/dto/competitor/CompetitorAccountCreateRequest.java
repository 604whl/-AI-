package com.shortvideoscripagent.xhsagentyunying.dto.competitor;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * S3-10 创建竞品账号请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorAccountCreateRequest {

    /** 平台代码（必填） */
    @NotBlank
    private String platformCode;

    /** 竞品账号名称（必填） */
    @NotBlank
    private String accountName;

    /** 竞品账号主页链接 */
    private String accountUrl;

    /** 平台侧账号 ID */
    private String externalAccountId;

    /** 粉丝数 */
    private Long followerCount;

    /** 品类代码 */
    private String category;

    /** 标签集合 */
    private List<String> tags;
}
