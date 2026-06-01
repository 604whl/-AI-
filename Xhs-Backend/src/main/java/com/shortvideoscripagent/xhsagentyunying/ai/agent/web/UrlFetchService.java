package com.shortvideoscripagent.xhsagentyunying.ai.agent.web;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

@Slf4j
@Service
public class UrlFetchService {

    private static final int CODE_FETCH_FAILED = 50006;
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final int MAX_BYTES = 512_000;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public FetchResult fetch(String url) {
        URI uri = validateUrl(url);
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "XhsAgent/1.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                throw new BusinessException(CODE_FETCH_FAILED, "fetch_url_http_error");
            }
            byte[] body = response.body();
            if (body.length > MAX_BYTES) {
                body = java.util.Arrays.copyOf(body, MAX_BYTES);
            }
            String contentType = response.headers().firstValue("content-type").orElse("");
            String text = extractText(body, contentType);
            return new FetchResult(uri.toString(), truncate(text, 4000), contentType);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("URL fetch failed for {}: {}", url, ex.getMessage());
            throw new BusinessException(CODE_FETCH_FAILED, "fetch_url_failed");
        }
    }

    private URI validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new BusinessException(CODE_FETCH_FAILED, "url_required");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (Exception ex) {
            throw new BusinessException(CODE_FETCH_FAILED, "url_invalid");
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new BusinessException(CODE_FETCH_FAILED, "url_scheme_not_allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BusinessException(CODE_FETCH_FAILED, "url_invalid");
        }
        assertNotPrivateHost(host);
        return uri;
    }

    private void assertNotPrivateHost(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || host.equalsIgnoreCase("localhost")) {
                throw new BusinessException(CODE_FETCH_FAILED, "url_private_not_allowed");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(CODE_FETCH_FAILED, "url_host_unresolved");
        }
    }

    private String extractText(byte[] body, String contentType) {
        String raw = new String(body, java.nio.charset.StandardCharsets.UTF_8);
        if (contentType.contains("html")) {
            raw = TAG_PATTERN.matcher(raw).replaceAll(" ");
            raw = raw.replaceAll("\\s+", " ").trim();
        }
        return raw;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    public record FetchResult(String url, String text, String contentType) {
    }
}
