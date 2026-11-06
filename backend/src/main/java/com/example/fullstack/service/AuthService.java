package com.example.fullstack.service;

import com.example.fullstack.config.JwtProperties;
import com.example.fullstack.dto.AuthRequest;
import com.example.fullstack.dto.AuthResponse;
import com.example.fullstack.dto.RegisterRequest;
import com.example.fullstack.entity.RefreshToken;
import com.example.fullstack.entity.Role;
import com.example.fullstack.entity.Tenant;
import com.example.fullstack.entity.User;
import com.example.fullstack.exception.ResourceAlreadyExistsException;
import com.example.fullstack.exception.ResourceNotFoundException;
import com.example.fullstack.exception.TokenRefreshException;
import com.example.fullstack.repository.RefreshTokenRepository;
import com.example.fullstack.repository.RoleRepository;
import com.example.fullstack.repository.TenantRepository;
import com.example.fullstack.repository.UserRepository;
import com.example.fullstack.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final JwtProperties jwtProperties;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResourceAlreadyExistsException("邮箱已被注册");
        }
        if (userRepository.existsByDisplayName(request.getUsername())) {
            throw new ResourceAlreadyExistsException("用户名已被使用");
        }

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name("ROLE_USER")
                        .description("普通成员")
                        .build()));

        // 新注册用户默认加入公共团队；后续由管理员划入正式团队
        Tenant defaultTenant = tenantRepository.findByName("默认团队")
                .orElseGet(() -> tenantRepository.save(Tenant.builder().name("默认团队").build()));

        Set<Role> roles = new HashSet<>();
        roles.add(userRole);

        User user = User.builder()
                .displayName(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .enabled(true)
                .tenant(defaultTenant)
                .roles(roles)
                .build();

        user = userRepository.save(user);
        IssuedToken issued = createRefreshToken(user);

        return buildAuthResponse(user, issued);
    }

    @Transactional
    public AuthResponse login(AuthRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = (User) authentication.getPrincipal();
        auditService.log(user.getTenant(), user.getId(), "LOGIN", user.getEmail());
        IssuedToken issued = createRefreshToken(user);

        return buildAuthResponse(user, issued);
    }

    /**
     * noRollbackFor：刷新失败的分支（过期/已撤销/重放）也要把"撤销令牌"的变更提交下去——
     * 否则事务回滚会吞掉撤销动作，重放攻击的家族撤销永远不落库。
     */
    @Transactional(noRollbackFor = TokenRefreshException.class)
    public AuthResponse refreshToken(String refreshTokenValue) {
        // 仅 refresh 类型的 JWT 可用于刷新（与 AccessToken 同签名，需显式校验用途）
        try {
            if (!JwtUtil.TYPE_REFRESH.equals(jwtUtil.extractType(refreshTokenValue))) {
                throw new TokenRefreshException("无效的刷新令牌");
            }
        } catch (TokenRefreshException e) {
            throw e;
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            throw new TokenRefreshException("刷新令牌无效或已过期");
        }

        RefreshToken storedToken = refreshTokenRepository.findByToken(hashToken(refreshTokenValue))
                .orElseThrow(() -> new TokenRefreshException("刷新令牌不存在或已失效"));

        if (storedToken.getRevoked()) {
            // 重用检测：已轮换令牌被重放说明可能泄露，撤销该用户全部刷新令牌并审计（令牌家族保护）
            User user = storedToken.getUser();
            revokeAllUserTokens(user.getId());
            auditService.log(user.getTenant(), user.getId(), "REFRESH_TOKEN_REUSE", user.getEmail());
            throw new TokenRefreshException("刷新令牌已被撤销");
        }

        if (storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            storedToken.setRevoked(true);
            refreshTokenRepository.save(storedToken);
            throw new TokenRefreshException("刷新令牌已过期，请重新登录");
        }

        // 撤销旧令牌，签发新令牌
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        User user = storedToken.getUser();
        IssuedToken issued = createRefreshToken(user);

        return buildAuthResponse(user, issued);
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        refreshTokenRepository.findByToken(hashToken(refreshTokenValue))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    /**
     * 自助修改密码（P1）：验证原密码后更新；成功后撤销该用户全部刷新令牌，
     * 强制所有已登录设备重新认证（密码泄露/改密后的标准处置）。
     */
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("原密码不正确");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("新密码不能与原密码相同");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        revokeAllUserTokens(userId);
        auditService.log(user.getTenant(), user.getId(), "CHANGE_PASSWORD", user.getEmail());
    }

    @Transactional
    public void revokeAllUserTokens(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        Set<RefreshToken> userTokens = refreshTokenRepository.findByUser(user);
        userTokens.forEach(token -> token.setRevoked(true));
        refreshTokenRepository.saveAll(userTokens);
    }

    /** 签发结果：原始令牌只用于本次响应（进 Cookie），库中仅存 SHA-256 哈希 */
    private record IssuedToken(String rawValue, RefreshToken entity) {}

    private IssuedToken createRefreshToken(User user) {
        String tokenValue = jwtUtil.generateRefreshToken(user);

        // 限制每个用户最多5个有效刷新令牌
        long activeTokens = refreshTokenRepository.countByUserAndRevokedFalse(user);
        if (activeTokens >= 5) {
            Set<RefreshToken> tokens = refreshTokenRepository.findByUser(user);
            tokens.stream()
                    .filter(t -> !t.getRevoked())
                    .findFirst()
                    .ifPresent(t -> {
                        t.setRevoked(true);
                        refreshTokenRepository.save(t);
                    });
        }

        RefreshToken refreshToken = RefreshToken.builder()
                .token(hashToken(tokenValue))
                .user(user)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtProperties.getRefreshExpiration() / 1000))
                .revoked(false)
                .build();

        return new IssuedToken(tokenValue, refreshTokenRepository.save(refreshToken));
    }

    private AuthResponse buildAuthResponse(User user, IssuedToken issuedToken) {
        return AuthResponse.builder()
                .accessToken(jwtUtil.generateToken(user))
                .refreshToken(issuedToken.rawValue())
                .expiresIn(jwtProperties.getExpiration())
                .tokenType("Bearer")
                .build();
    }

    /** RefreshToken 静态加密加固：库中存 SHA-256(token)，泄露数据库不等于泄露可用令牌 */
    private static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
