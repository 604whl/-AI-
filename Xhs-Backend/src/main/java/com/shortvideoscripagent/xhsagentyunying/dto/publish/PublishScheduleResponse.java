package com.shortvideoscripagent.xhsagentyunying.dto.publish;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 排期响应。
 */
@Data
@Builder
public class PublishScheduleResponse {

    /** 排期 ID */
    private String id;

    /** 内容资产 ID */
    private String assetId;

    /** 内容标题（便于日历直接展示） */
    private String title;

    /** 资产当前状态（scheduled / approved / published ...） */
    private String assetStatus;

    private String platformCode;

    private String accountId;

    /** 排期时刻（UTC 落库值） */
    private OffsetDateTime scheduledAt;

    /** 排期时刻在排期时区下的本地时间 */
    private OffsetDateTime scheduledAtLocal;

    /** IANA 时区串 */
    private String timezone;

    /** scheduled / executing / succeeded / failed / cancelled / expired */
    private String status;

    /** 关联的发布任务 ID */
    private String publishTaskId;

    /** 失败错误码 */
    private Integer errorCode;

    /** 失败原因（可读文本） */
    private String errorMessage;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
