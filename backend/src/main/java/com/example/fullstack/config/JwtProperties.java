package com.example.fullstack.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    private String secret;
    private Long expiration;
    private Long refreshExpiration;
    /** RefreshToken Cookie 是否带 Secure 标记；生产环境（HTTPS）必须为 true */
    private boolean cookieSecure;

    @PostConstruct
    public void validate() {
        if (secret == null || secret.isBlank()) {
            log.warn("JWT secret is not configured. Using default secret for development purposes only.");
            secret = "default-development-secret-key-must-be-at-least-32-characters-long-for-production";
        }
        if (secret.length() < 32) {
            log.warn("JWT secret is less than 32 characters. For HS256, use at least 32 characters (256 bits).");
        }
    }
}