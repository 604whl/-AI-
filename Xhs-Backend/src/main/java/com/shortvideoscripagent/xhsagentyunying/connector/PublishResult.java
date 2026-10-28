package com.shortvideoscripagent.xhsagentyunying.connector;

import java.time.OffsetDateTime;

/**
 * 发布结果。
 * <p>
 * <b>关键约束</b>：无法自动发布时 {@code manual=true} 且 {@code externalContentId} 必须为
 * {@code null} —— 严禁伪造外部内容 ID。
 *
 * @param manual            是否需要人工在平台页面确认
 * @param externalContentId 平台侧内容 ID；manual=true 时必须为 null
 * @param sourceUrl         平台侧内容链接
 * @param publishedAt       实际发布时间
 * @param errorCode         错误码（失败时）
 * @param errorMessage      错误描述（失败时）
 */
public record PublishResult(boolean manual, String externalContentId, String sourceUrl,
                             OffsetDateTime publishedAt, String errorCode, String errorMessage) {

    /**
     * 需要用户在平台页面手动确认。
     * <p>
     * 典型场景：{@code BrowserAssistConnector}（系统只做填充，不自动点发布）、
     * {@code ManualImportConnector}（纯人工导入）。
     */
    public static PublishResult manualRequired() {
        return new PublishResult(true, null, null, null, null,
                "MANUAL_CONFIRMATION_REQUIRED: 需用户在平台页面确认后完成发布");
    }

    /**
     * 发布失败。
     */
    public static PublishResult failure(String errorCode, String errorMessage) {
        return new PublishResult(false, null, null, null, errorCode, errorMessage);
    }
}
