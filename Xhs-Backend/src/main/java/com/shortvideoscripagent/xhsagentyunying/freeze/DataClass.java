package com.shortvideoscripagent.xhsagentyunying.freeze;

/**
 * 阶段 0：数据保留期限类别。
 * <p>
 * 默认值见 <code>app.data_retention_policy</code> 表：
 * <ul>
 *   <li>{@link #CONTENT}：内容资产 365 天</li>
 *   <li>{@link #MEDIA}：媒体文件 180 天</li>
 *   <li>{@link #REPORT}：分析报告 180 天</li>
 *   <li>{@link #AUDIT_LOG}：操作日志 365 天</li>
 *   <li>{@link #DELETION_REQUEST}：删除请求记录 90 天</li>
 *   <li>{@link #EXPORT}：导出文件 7 天（短期）</li>
 *   <li>{@link #CHAT_SESSION}：会话 180 天</li>
 * </ul>
 */
public enum DataClass {
    CONTENT,
    MEDIA,
    REPORT,
    AUDIT_LOG,
    DELETION_REQUEST,
    EXPORT,
    CHAT_SESSION
}
