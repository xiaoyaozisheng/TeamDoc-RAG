package com.example.fullstack.controller;

import com.example.fullstack.dto.AuthRequest;
import com.example.fullstack.dto.AuthResponse;
import com.example.fullstack.dto.RegisterRequest;
import com.example.fullstack.dto.UserResponse;
import com.example.fullstack.entity.User;
import com.example.fullstack.exception.TokenRefreshException;
import com.example.fullstack.service.AuthService;
import com.example.fullstack.service.UserService;
import com.example.fullstack.config.JwtProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "认证", description = "用户认证相关接口")
public class AuthController {

    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    /** Cookie 仅在认证路径下可见，缩小暴露面 */
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final UserService userService;
    private final JwtProperties jwtProperties;

    @Operation(summary = "用户注册", description = "注册新用户并返回JWT令牌；RefreshToken 通过 HttpOnly Cookie 下发")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "注册成功",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "参数验证失败"),
            @ApiResponse(responseCode = "409", description = "邮箱或用户名已存在")
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return withRefreshCookie(authService.register(request));
    }

    @Operation(summary = "用户登录", description = "使用邮箱和密码登录；RefreshToken 通过 HttpOnly Cookie 下发")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "登录成功",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "认证失败")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return withRefreshCookie(authService.login(request));
    }

    @Operation(summary = "刷新令牌", description = "依赖浏览器自动携带的 refresh_token Cookie；响应通过 Set-Cookie 轮换新 RefreshToken")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "刷新成功",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "刷新令牌无效或已过期")
    })
    @PostMapping("/refresh-token")
    public ResponseEntity<AuthResponse> refreshToken(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new TokenRefreshException("缺少刷新令牌，请重新登录");
        }
        return withRefreshCookie(authService.refreshToken(refreshToken));
    }

    @Operation(summary = "用户登出", description = "撤销 Cookie 中的刷新令牌并清除 Cookie")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .build();
    }

    @Operation(summary = "获取当前用户信息", description = "获取已登录用户的详细信息")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "获取成功",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "未认证")
    })
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getUserInfo(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(userService.getUserById(user.getId()));
    }

    /** RefreshToken 只经 HttpOnly Cookie 传输，不出现在响应体中 */
    private ResponseEntity<AuthResponse> withRefreshCookie(AuthResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, response.getRefreshToken())
                .httpOnly(true)      // JS 不可读，防 XSS 窃取
                .secure(jwtProperties.isCookieSecure())  // 生产环境必须 HTTPS
                .sameSite("Strict")  // 防 CSRF
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ofMillis(jwtProperties.getRefreshExpiration()))
                .build();
        response.setRefreshToken(null);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }
}
