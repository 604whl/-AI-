package com.shortvideoscripagent.xhsagentyunying.infra.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URL;
import java.util.List;
import java.util.regex.Pattern;

/**
 * SSRF 防护：URL 抓取前校验目标 IP 是否属于内网/保留地址段。
 * <p>
 * 防护范围：
 * <ul>
 *   <li>直接 IP 访问（如 127.0.0.1、169.254.169.254）</li>
 *   <li>域名解析后命中内网 IP</li>
 *   <li>HTTP redirect 链（最多 3 跳，每跳重新校验）</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class UrlPolicyGuard {

    private static final int MAX_REDIRECT_HOPS = 3;
    /** 响应体最大 10MB */
    public static final long MAX_RESPONSE_BYTES = 10 * 1024 * 1024;

    /**
     * RFC 5735 / IANA 保留地址段（简化 CIDR 表示）。
     * 格式：ipPrefix/maskBits
     */
    private static final List<IpRange> BLOCKED_RANGES = List.of(
            // Loopback: 127.0.0.0/8
            new IpRange("127.0.0.0", 8),
            // Link-local: 169.254.0.0/16 (EC2 metadata 等)
            new IpRange("169.254.0.0", 16),
            // Private: 10.0.0.0/8
            new IpRange("10.0.0.0", 8),
            // Private: 172.16.0.0/12
            new IpRange("172.16.0.0", 12),
            // Private: 192.168.0.0/16
            new IpRange("192.168.0.0", 16),
            // CGN/shared: 100.64.0.0/10
            new IpRange("100.64.0.0", 10),
            // Multicast: 224.0.0.0/4
            new IpRange("224.0.0.0", 4),
            // Reserved/broadcast: 240.0.0.0/4
            new IpRange("240.0.0.0", 4),
            // IPv6 loopback
            new IpRange("::1", 128),
            // IPv6 link-local
            new IpRange("fe80:", 10),
            // IPv6 unique local
            new IpRange("fc00:", 7),
            new IpRange("fd00:", 7)
    );

    /**
     * 校验 URL 是否允许访问。
     *
     * @param urlStr URL 字符串
     * @return 校验通过的解析后 IP（用于后续连接）
     * @throws SecurityException 若 URL 命中黑名单
     */
    public String validateUrl(String urlStr) throws SecurityException {
        if (urlStr == null || urlStr.isBlank()) {
            throw new SecurityException("URL cannot be empty");
        }
        try {
            URL url = new URL(urlStr);
            return validateHost(url.getHost(), urlStr);
        } catch (Exception e) {
            throw new SecurityException("Invalid URL: " + e.getMessage());
        }
    }

    /**
     * 校验 redirect 跳转 URL。
     *
     * @param location 重定向目标（相对或绝对 URL）
     * @param baseUrl  来源 URL（用于解析相对路径）
     * @throws SecurityException 若跳转命中黑名单
     */
    public void validateRedirect(String location, String baseUrl) throws SecurityException {
        if (location == null || location.isBlank()) {
            return;
        }
        try {
            URL base = new URL(baseUrl);
            URL resolved = new URL(base, location);
            validateHost(resolved.getHost(), "redirect to " + location);
        } catch (Exception e) {
            throw new SecurityException("Invalid redirect URL: " + e.getMessage());
        }
    }

    /**
     * 从 redirect header 提取并校验所有 Location。
     *
     * @param locationHeader HTTP Location header 值
     * @param baseUrl 当前 URL（用于解析相对路径）
     * @throws SecurityException 若任一跳转命中黑名单
     */
    public void validateRedirectHeader(String locationHeader, String baseUrl) throws SecurityException {
        if (locationHeader == null || locationHeader.isBlank()) {
            return;
        }
        // 可能有多个 location（HTTP/2 pseudo-headers deduplication），取第一个
        String location = locationHeader.split("[,\r\n]")[0].trim();
        validateRedirect(location, baseUrl);
    }

    private String validateHost(String host, String context) throws SecurityException {
        if (host == null || host.isBlank()) {
            throw new SecurityException("Host cannot be empty in " + context);
        }

        // 先检查是否是 IP 字面量
        if (looksLikeIpv4(host)) {
            checkBlockedIp(host, context);
            return host;
        }
        if (looksLikeIpv6(host)) {
            checkBlockedIp(normalizeIpv6(host), context);
            return host;
        }

        // 域名：DNS 解析
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                String ip = addr.getHostAddress();
                checkBlockedIp(ip, context);
            }
            return addresses[0].getHostAddress();
        } catch (Exception e) {
            log.warn("DNS resolution failed for {}: {}", host, e.getMessage());
            throw new SecurityException("Cannot resolve host: " + host);
        }
    }

    private void checkBlockedIp(String ip, String context) throws SecurityException {
        if (ip == null) return;
        String normalized = normalizeIpv6(ip);
        for (IpRange range : BLOCKED_RANGES) {
            if (range.contains(normalized)) {
                log.warn("SSRF blocked: {} resolved to blocked IP {} (range {}) in context {}",
                        context, ip, range, normalized);
                throw new SecurityException("Access to internal/reserved IP is forbidden: " + ip);
            }
        }
    }

    private boolean looksLikeIpv4(String host) {
        return IPV4_PATTERN.matcher(host).matches();
    }

    private boolean looksLikeIpv6(String host) {
        return host.contains(":");
    }

    private String normalizeIpv6(String ip) {
        // 简化：保留前缀用于匹配
        if (ip.startsWith("[")) {
            ip = ip.substring(1, ip.length() - 1);
        }
        return ip.toLowerCase();
    }

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");

    private record IpRange(String prefix, int maskBits) {
        /**
         * 检查给定 IP（IPv4 点分或 IPv6 冒号格式）是否在范围内。
         */
        boolean contains(String ip) {
            if (ip == null) return false;
            try {
                if (ip.contains(":")) {
                    return ipv6Contains(prefix, ip);
                } else {
                    return ipv4Contains(prefix, maskBits, ip);
                }
            } catch (Exception e) {
                return false;
            }
        }

        private boolean ipv4Contains(String networkPrefix, int maskBits, String ip) {
            long network = ipToLong(networkPrefix);
            long host = ipToLong(ip);
            long mask = (-1L) << (32 - maskBits);
            return (network & mask) == (host & mask);
        }

        private boolean ipv6Contains(String prefix, String ip) {
            // 简化：仅比较前缀
            String normalizedIp = normalizeIpv6(ip);
            return normalizedIp.startsWith(prefix.toLowerCase());
        }

        private String normalizeIpv6(String ip) {
            // 简化：保留前缀用于匹配
            if (ip == null) return "";
            String normalized = ip;
            if (normalized.startsWith("[")) {
                normalized = normalized.substring(1, normalized.length() - 1);
            }
            return normalized.toLowerCase();
        }

        private long ipToLong(String ip) {
            String[] parts = ip.split("\\.");
            long result = 0;
            for (String part : parts) {
                result = (result << 8) | (Long.parseLong(part) & 0xFF);
            }
            return result;
        }
    }

    /** 通用安全异常（不泄露内部细节） */
    public static class SecurityException extends Exception {
        public SecurityException(String message) {
            super(message);
        }
    }
}
