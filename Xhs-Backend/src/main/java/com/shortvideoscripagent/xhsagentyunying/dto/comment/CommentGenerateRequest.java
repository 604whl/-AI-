package com.shortvideoscripagent.xhsagentyunying.dto.comment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 评论生成请求 DTO。
 * <p>
 * 支持 4 种场景：首问回复、追问、私信话术、转化话术。
 */
@Data
public class CommentGenerateRequest {

    /**
     * 评论类型枚举。
     */
    @NotNull(message = "commentType is required")
    private CommentType commentType;

    /**
     * 目标平台（XIAOHONGSHU / DOUYIN / ...）。
     * 仅记录，不做差异化生成。
     */
    @NotBlank(message = "platform is required")
    @Size(max = 32, message = "platform max 32 chars")
    private String platform;

    /**
     * 人设风格：agency（品牌 / 机构）/ mentor（创作者 IP）/ senior（资深用户）。
     */
    @Size(max = 32, message = "persona max 32 chars")
    private String persona;

    /**
     * 正文/标题上下文，最大 5000 字。
     */
    @Size(max = 5000, message = "draftContext max 5000 chars")
    private String draftContext;

    /**
     * 最大回复字数，默认 200。
     */
    @Max(value = 500, message = "maxLength max 500")
    private Integer maxLength = 200;
}