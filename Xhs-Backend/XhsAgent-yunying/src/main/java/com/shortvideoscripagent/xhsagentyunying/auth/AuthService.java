package com.shortvideoscripagent.xhsagentyunying.auth;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shortvideoscripagent.xhsagentyunying.auth.dto.AuthResponse;
import com.shortvideoscripagent.xhsagentyunying.auth.dto.LoginRequest;
import com.shortvideoscripagent.xhsagentyunying.auth.dto.RegisterRequest;
import com.shortvideoscripagent.xhsagentyunying.auth.dto.TokenRefreshResponse;
import com.shortvideoscripagent.xhsagentyunying.auth.dto.UserProfileVo;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.User;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int CODE_UNAUTHORIZED = 40101;
    private static final int CODE_EMAIL_EXISTS = 40903;

    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (findByEmail(email) != null) {
            throw new BusinessException(CODE_EMAIL_EXISTS, "该邮箱已注册");
        }

        OffsetDateTime now = OffsetDateTime.now();
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(BCrypt.hashpw(request.getPassword()));
        user.setDisplayName(blankToNull(request.getDisplayName()));
        user.setDefaultPersona("agency");
        user.setDailyQuota(3);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);

        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());
        User user = findByEmail(email);
        if (user == null || !BCrypt.checkpw(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(CODE_UNAUTHORIZED, "邮箱或密码错误");
        }
        return buildAuthResponse(user);
    }

    public TokenRefreshResponse refresh(String refreshToken) {
        Claims claims = parseRefreshClaims(refreshToken);
        String jti = claims.getId();
        Long userId = refreshTokenStore.getUserId(jti);
        if (userId == null || !String.valueOf(userId).equals(claims.getSubject())) {
            throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        }

        refreshTokenStore.revoke(jti);
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        }

        JwtTokenProvider.IssuedToken access = jwtTokenProvider.createAccessToken(user.getId());
        JwtTokenProvider.IssuedToken refresh = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenStore.save(refresh.jti(), user.getId(), refresh.expiresInSeconds());

        return TokenRefreshResponse.builder()
                .accessToken(access.token())
                .refreshToken(refresh.token())
                .expiresIn(access.expiresInSeconds())
                .build();
    }

    public UserProfileVo getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        }
        return toProfile(user);
    }

    public Long resolveUserIdFromAccessToken(String token) {
        try {
            Claims claims = jwtTokenProvider.parseClaims(token);
            if (!JwtTokenProvider.TYPE_ACCESS.equals(claims.get(JwtTokenProvider.CLAIM_TYPE, String.class))) {
                return null;
            }
            return Long.parseLong(claims.getSubject());
        } catch (JwtException | NumberFormatException ex) {
            return null;
        }
    }

    private Claims parseRefreshClaims(String refreshToken) {
        try {
            Claims claims = jwtTokenProvider.parseClaims(refreshToken);
            if (!JwtTokenProvider.TYPE_REFRESH.equals(claims.get(JwtTokenProvider.CLAIM_TYPE, String.class))) {
                throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
            }
            return claims;
        } catch (ExpiredJwtException ex) {
            throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        } catch (JwtException ex) {
            throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        }
    }

    private AuthResponse buildAuthResponse(User user) {
        JwtTokenProvider.IssuedToken access = jwtTokenProvider.createAccessToken(user.getId());
        JwtTokenProvider.IssuedToken refresh = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenStore.save(refresh.jti(), user.getId(), refresh.expiresInSeconds());

        return AuthResponse.builder()
                .accessToken(access.token())
                .refreshToken(refresh.token())
                .expiresIn(access.expiresInSeconds())
                .user(toProfile(user))
                .build();
    }

    private User findByEmail(String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
    }

    private UserProfileVo toProfile(User user) {
        return UserProfileVo.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .defaultPersona(user.getDefaultPersona())
                .dailyQuota(user.getDailyQuota())
                .build();
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
