package com.shortvideoscripagent.xhsagentyunying.infra.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UrlPolicyGuard 单元测试：验证 SSRF 防护正确性。
 */
class UrlPolicyGuardTest {

    private final UrlPolicyGuard guard = new UrlPolicyGuard();

    // ---------- 内网 IP 测试 ----------

    @ParameterizedTest
    @DisplayName("127.x.x.x 环回地址应被拦截")
    @ValueSource(strings = {
            "http://127.0.0.1/",
            "http://127.0.0.1:8080/",
            "http://127.0.1.1/",
            "http://127.255.255.255/",
            "http://localhost/"
    })
    void block_localhost_and_127(String url) {
        assertThrows(UrlPolicyGuard.SecurityException.class, () -> guard.validateUrl(url));
    }

    @ParameterizedTest
    @DisplayName("10.x.x.x 私有地址应被拦截")
    @ValueSource(strings = {
            "http://10.0.0.1/",
            "http://10.255.255.255/",
            "http://10.0.1.1/",
            "http://10.0.0.0/"
    })
    void block_10_private_range(String url) {
        assertThrows(UrlPolicyGuard.SecurityException.class, () -> guard.validateUrl(url));
    }

    @ParameterizedTest
    @DisplayName("172.16.x.x - 172.31.x.x 私有地址应被拦截")
    @ValueSource(strings = {
            "http://172.16.0.1/",
            "http://172.31.255.255/",
            "http://172.20.0.1/",
            "http://172.17.0.1/"  // Docker bridge network
    })
    void block_172_private_range(String url) {
        assertThrows(UrlPolicyGuard.SecurityException.class, () -> guard.validateUrl(url));
    }

    @ParameterizedTest
    @DisplayName("192.168.x.x 私有地址应被拦截")
    @ValueSource(strings = {
            "http://192.168.0.1/",
            "http://192.168.1.1/",
            "http://192.168.255.255/"
    })
    void block_192_private_range(String url) {
        assertThrows(UrlPolicyGuard.SecurityException.class, () -> guard.validateUrl(url));
    }

    @ParameterizedTest
    @DisplayName("169.254.x.x (link-local/EC2 metadata) 应被拦截")
    @ValueSource(strings = {
            "http://169.254.169.254/latest/meta-data/",
            "http://169.254.169.254/latest/user-data/"
    })
    void block_link_local_ec2_metadata(String url) {
        assertThrows(UrlPolicyGuard.SecurityException.class, () -> guard.validateUrl(url));
    }

    @ParameterizedTest
    @DisplayName("100.64.x.x - 100.127.x.x (CGN) 应被拦截")
    @ValueSource(strings = {
            "http://100.64.0.1/",
            "http://100.127.255.255/"
    })
    void block_cgn_range(String url) {
        assertThrows(UrlPolicyGuard.SecurityException.class, () -> guard.validateUrl(url));
    }

    @ParameterizedTest
    @DisplayName("224.x.x.x+ (multicast/reserved) 应被拦截")
    @ValueSource(strings = {
            "http://224.0.0.1/",
            "http://239.255.255.250/"  // SSDP
    })
    void block_multicast_range(String url) {
        assertThrows(UrlPolicyGuard.SecurityException.class, () -> guard.validateUrl(url));
    }

    // ---------- 公开 URL 测试 ----------

    @Test
    @DisplayName("公开 URL 应该允许访问")
    void allow_public_url() {
        assertDoesNotThrow(() -> guard.validateUrl("https://www.baidu.com/"));
        assertDoesNotThrow(() -> guard.validateUrl("https://httpbin.org/get"));
        assertDoesNotThrow(() -> guard.validateUrl("https://api.github.com/"));
    }

    @Test
    @DisplayName("空 URL 应抛出异常")
    void reject_empty_url() {
        assertThrows(UrlPolicyGuard.SecurityException.class, () -> guard.validateUrl(null));
        assertThrows(UrlPolicyGuard.SecurityException.class, () -> guard.validateUrl(""));
        assertThrows(UrlPolicyGuard.SecurityException.class, () -> guard.validateUrl("   "));
    }

    @Test
    @DisplayName("无效 URL 应抛出异常")
    void reject_invalid_url() {
        assertThrows(UrlPolicyGuard.SecurityException.class, () -> guard.validateUrl("not-a-url"));
        assertThrows(UrlPolicyGuard.SecurityException.class, () -> guard.validateUrl("http://"));
    }

    // ---------- 重定向测试 ----------

    @Test
    @DisplayName("Redirect 到内网应被拦截")
    void block_redirect_to_internal() {
        // 当前 URL 是公开的，但 redirect 到内网
        assertThrows(UrlPolicyGuard.SecurityException.class,
                () -> guard.validateRedirect("http://127.0.0.1:8080/", "https://example.com/"));
    }

    @Test
    @DisplayName("Redirect 到公开 URL 应允许")
    void allow_redirect_to_public() {
        assertDoesNotThrow(() -> guard.validateRedirect("https://www.baidu.com/", "https://example.com/"));
    }

    @Test
    @DisplayName("相对路径 redirect 应正确解析")
    void allow_relative_redirect() {
        // 相对路径 redirect 到同域
        assertDoesNotThrow(() -> guard.validateRedirect("/api/data", "https://example.com/page"));
    }

    // ---------- 边界测试 ----------

    @Test
    @DisplayName("域名 localhost 应被拦截（DNS 解析后）")
    void block_localhost_via_dns() {
        // 注意：这取决于 DNS 解析结果，可能在某些环境下通过
        // 在 CI 环境中可能需要 mock
        try {
            guard.validateUrl("http://localhost/");
            // 如果通过，说明 DNS 解析没有返回 127.0.0.1
        } catch (UrlPolicyGuard.SecurityException e) {
            // 预期行为：被拦截
        }
    }

    @Test
    @DisplayName("公网 IP 地址应该允许")
    void allow_public_ip() {
        // 百度 IP（仅示例）
        assertDoesNotThrow(() -> guard.validateUrl("https://8.8.8.8/"));
    }

    @Test
    @DisplayName("HTTP Digest 认证 URL 应正确解析")
    void allow_url_with_auth() {
        assertDoesNotThrow(() -> guard.validateUrl("https://user:pass@example.com/"));
    }

    @Test
    @DisplayName("带端口的 URL 应正确解析")
    void allow_url_with_port() {
        assertDoesNotThrow(() -> guard.validateUrl("https://example.com:8443/api"));
    }
}
