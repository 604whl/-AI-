package com.shortvideoscripagent.xhsagentyunying.freeze;

/**
 * 阶段 0：平台连接等级。
 * <p>
 * 任何新连接器必须明确等级，禁止"我也不知道算自动还是手动"。
 * <p>
 * 等级与数据采集的合规风险一一对应，越往下风险越高。
 */
public enum PlatformConnectorLevel {
    /** 官方 API：平台授权的官方接口 */
    OFFICIAL_API,
    /** 授权连接器：经授权的第三方数据服务 */
    AUTHORIZED_CONNECTOR,
    /** 浏览器辅助：用户在平台页面确认，系统填充和回收 */
    BROWSER_ASSIST,
    /** 手动导入：CSV/Excel/截图/链接/媒体上传 */
    MANUAL_IMPORT,
    /** 禁用：明确不允许接入 */
    DISABLED;

    public boolean canWrite() {
        return this == OFFICIAL_API || this == AUTHORIZED_CONNECTOR || this == BROWSER_ASSIST;
    }

    public boolean canRead() {
        return this != DISABLED;
    }

    public boolean requiresLegalReview() {
        return this == BROWSER_ASSIST || this == AUTHORIZED_CONNECTOR || this == OFFICIAL_API;
    }
}
