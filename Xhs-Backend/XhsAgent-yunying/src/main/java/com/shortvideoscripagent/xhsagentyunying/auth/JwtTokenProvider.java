package com.shortvideoscripagent.xhsagentyunying.auth;

import com.shortvideoscripagent.xhsagentyunying.config.AppAuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    public static final String CLAIM_TYPE = "type";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final AppAuthProperties authProperties;

    private SecretKey secretKey;

    @PostConstruct
    void init() {
        byte[] keyBytes = authProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("app.auth.jwt-secret must be at least 32 bytes");
        }
        secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public IssuedToken createAccessToken(Long userId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(authProperties.getAccessTokenExpireSeconds());
        String token = Jwts.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
        return new IssuedToken(token, authProperties.getAccessTokenExpireSeconds());
    }

    public IssuedToken createRefreshToken(Long userId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(authProperties.getRefreshTokenExpireSeconds());
        String jti = UUID.randomUUID().toString().replace("-", "");
        String token = Jwts.builder()
                .id(jti)
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
        return new IssuedToken(token, jti, authProperties.getRefreshTokenExpireSeconds());
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isExpired(JwtException ex) {
        return ex instanceof ExpiredJwtException;
    }

    public record IssuedToken(String token, String jti, long expiresInSeconds) {
        public IssuedToken(String token, long expiresInSeconds) {
            this(token, null, expiresInSeconds);
        }
    }
}
