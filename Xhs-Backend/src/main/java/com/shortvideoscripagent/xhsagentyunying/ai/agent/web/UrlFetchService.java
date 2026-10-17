package com.shortvideoscripagent.xhsagentyunying.ai.agent.web;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.config.AppAgentProperties;
import com.shortvideoscripagent.xhsagentyunying.infra.security.UrlPolicyGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * URL 抓取服务，供 Agent 工具调用。
 * <p>
 * 内置 SSRF 防护：校验目标 IP 是否属于内网/保留地址段。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UrlFetchService {

    private static final int CODE_BLOCKED = 40301;
    private static final int CODE_FETCH_ERROR = 50002;

    private static final int MAX_REDIRECTS = 3;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final AppAgentProperties agentProperties;
    private final UrlPolicyGuard urlPolicyGuard;

    /**
     * 抓取 URL 内容。
     *
     * @param url 目标 URL
     * @return 抓取结果
     */
    public FetchResult fetch(String url) {
        if (!agentProperties.getTools().isFetchUrl()) {
            throw new BusinessException(40301, "tool_disabled");
        }

        try {
            // SSRF 校验
            urlPolicyGuard.validateUrl(url);

            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(TIMEOUT)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "XhsAgent/1.0")
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            int statusCode = response.statusCode();
            String contentType = response.headers().firstValue("Content-Type").orElse("");

            // 检查响应大小
            byte[] body = response.body();
            if (body != null && body.length > UrlPolicyGuard.MAX_RESPONSE_BYTES) {
                log.warn("URL fetch response too large: {} bytes for {}", body.length, url);
                throw new BusinessException(CODE_FETCH_ERROR, "response_too_large");
            }

            return new FetchResult(
                    statusCode,
                    contentType,
                    body,
                    response.headers().firstValue("Content-Encoding").orElse("")
            );
        } catch (UrlPolicyGuard.SecurityException e) {
            log.warn("SSRF blocked: {}", url);
            throw new BusinessException(CODE_BLOCKED, "url_blocked_by_policy");
        } catch (IOException e) {
            log.error("URL fetch IO error: {} -> {}", url, e.getMessage());
            throw new BusinessException(CODE_FETCH_ERROR, "fetch_error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(CODE_FETCH_ERROR, "fetch_interrupted");
        }
    }

    /**
     * 抓取 URL 并返回文本内容（自动判断编码）。
     */
    public FetchTextResult fetchText(String url) {
        FetchResult result = fetch(url);
        String text = decodeBody(result.body(), result.contentEncoding());
        return new FetchTextResult(result.statusCode(), result.contentType(), text);
    }

    private String decodeBody(byte[] body, String contentEncoding) {
        if (body == null) return "";
        try {
            if ("gzip".equalsIgnoreCase(contentEncoding)) {
                java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(body);
                java.util.zip.GZIPInputStream gzip = new java.util.zip.GZIPInputStream(bais);
                return new String(gzip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            return new String(body, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return new String(body, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    public record FetchResult(int statusCode, String contentType, byte[] body, String contentEncoding) {
    }

    public record FetchTextResult(int statusCode, String contentType, String text) {
    }
}
