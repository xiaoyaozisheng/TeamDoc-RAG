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
        RefreshToken savedRefreshToken = createRefreshToken(user);

        return buildAuthResponse(user, savedRefreshToken);
    }

    public AuthResponse login(AuthRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = (User) authentication.getPrincipal();
        auditService.log(user.getTenant(), user.getId(), "LOGIN", user.getEmail());
        RefreshToken savedRefreshToken = createRefreshToken(user);

        return buildAuthResponse(user, savedRefreshToken);
    }

    @Transactional
    public AuthResponse refreshToken(String refreshTokenValue) {
        RefreshToken storedToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new TokenRefreshException("刷新令牌不存在或已失效"));

        if (storedToken.getRevoked()) {
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
        RefreshToken newRefreshToken = createRefreshToken(user);

        return buildAuthResponse(user, newRefreshToken);
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        refreshTokenRepository.findByToken(refreshTokenValue)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    @Transactional
    public void revokeAllUserTokens(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        Set<RefreshToken> userTokens = refreshTokenRepository.findByUser(user);
        userTokens.forEach(token -> token.setRevoked(true));
        refreshTokenRepository.saveAll(userTokens);
    }

    private RefreshToken createRefreshToken(User user) {
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
                .token(tokenValue)
                .user(user)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtProperties.getRefreshExpiration() / 1000))
                .revoked(false)
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    private AuthResponse buildAuthResponse(User user, RefreshToken refreshToken) {
        return AuthResponse.builder()
                .accessToken(jwtUtil.generateToken(user))
                .refreshToken(refreshToken.getToken())
                .expiresIn(jwtProperties.getExpiration())
                .tokenType("Bearer")
                .build();
    }
}
