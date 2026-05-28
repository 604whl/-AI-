package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.auth.AuthService;
import com.shortvideoscripagent.xhsagentyunying.auth.dto.AuthResponse;
import com.shortvideoscripagent.xhsagentyunying.auth.dto.LoginRequest;
import com.shortvideoscripagent.xhsagentyunying.auth.dto.RefreshTokenRequest;
import com.shortvideoscripagent.xhsagentyunying.auth.dto.RegisterRequest;
import com.shortvideoscripagent.xhsagentyunying.auth.dto.TokenRefreshResponse;
import com.shortvideoscripagent.xhsagentyunying.auth.dto.UserProfileVo;
import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final int CODE_UNAUTHORIZED = 40101;

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenRefreshResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(authService.refresh(request.getRefreshToken()));
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileVo> me() {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        }
        return ApiResponse.ok(authService.getProfile(userId));
    }
}
