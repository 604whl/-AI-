package com.shortvideoscripagent.xhsagentyunying.freeze;

import java.util.Optional;

/**
 * 阶段 0：平台适配器代码枚举。
 * <p>
 * 与数据库表 {@code app.platform} 一一对应。修改本枚举需同步 {@code V34__stage0_freeze.sql}。
 * <p>
 * 平台适配器能力受 {@link PlatformConnectorLevel} 限制，不允许平台绕过等级声明能力。
 */
public enum PlatformCode {
    XIAOHONGSHU("xiaohongshu", "小红书"),
    DOUYIN("douyin", "抖音"),
    WECHAT_OA("wechat_oa", "微信公众号"),
    WECHAT_CHAN("wechat_chan", "视频号"),
    BILIBILI("bilibili", "哔哩哔哩"),
    WEIBO("weibo", "微博"),
    ZHIHU("zhihu", "知乎"),
    KUAISHOU("kuaishou", "快手"),
    INSTAGRAM("instagram", "Instagram"),
    TIKTOK("tiktok", "TikTok");

    private final String slug;
    private final String displayName;

    PlatformCode(String slug, String displayName) {
        this.slug = slug;
        this.displayName = displayName;
    }

    public String getSlug() {
        return slug;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Optional<PlatformCode> tryParse(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(PlatformCode.valueOf(code.trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
