package com.example.fullstack.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;

@Data
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    private String secret;
    private Long expiration;
    private Long refreshExpiration;
    /** RefreshToken Cookie 是否带 Secure 标记；生产环境（HTTPS）必须为 true，可用 COOKIE_SECURE 环境变量覆盖 */
    private boolean cookieSecure;

    /** 仅供开发兜底的默认密钥；生产 Profile 下禁止使用 */
    static final String DEV_FALLBACK_SECRET = "default-development-secret-key-must-be-at-least-32-characters-long-for-production";

    private final Environment environment;

    public JwtProperties(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        boolean secureProfile = Arrays.asList(environment.getActiveProfiles()).contains("prod")
                || Arrays.asList(environment.getActiveProfiles()).contains("postgres");

        if (secret == null || secret.isBlank()) {
            if (secureProfile) {
                // 默认部署（postgres/prod）即暴露公网时必须 fail-fast，否则令牌可被任意伪造
                throw new IllegalStateException(
                        "生产 Profile（postgres/prod）必须通过 JWT_SECRET 环境变量配置至少 32 字符的强密钥");
            }
            log.warn("JWT secret 未配置，使用开发默认密钥（仅限本地开发）");
            secret = DEV_FALLBACK_SECRET;
            return;
        }
        if (DEV_FALLBACK_SECRET.equals(secret)) {
            log.warn("JWT secret 为开发默认值，切勿用于任何对外环境");
        }
        if (secret.length() < 32 && secureProfile) {
            throw new IllegalStateException("JWT_SECRET 长度不足 32 字符（HS256 需要至少 256 位密钥）");
        }
        if (secret.length() < 32) {
            log.warn("JWT secret 少于 32 字符（HS256 建议 256 位），仅限开发环境");
        }
    }
}
