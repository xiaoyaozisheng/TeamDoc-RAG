package com.example.fullstack.controller;

import com.example.fullstack.dto.AuthRequest;
import com.example.fullstack.dto.RegisterRequest;
import com.example.fullstack.entity.Role;
import com.example.fullstack.entity.User;
import com.example.fullstack.repository.RefreshTokenRepository;
import com.example.fullstack.repository.RoleRepository;
import com.example.fullstack.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private com.example.fullstack.repository.TenantRepository tenantRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private com.example.fullstack.repository.ConversationRepository conversationRepository;

    @Autowired
    private com.example.fullstack.repository.ChunkRepository chunkRepository;

    @Autowired
    private com.example.fullstack.repository.DocumentRepository documentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        chunkRepository.deleteAll();
        documentRepository.deleteAll();
        conversationRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();

        // find-or-create，避免与其他测试类共享文件库时租户名唯一键冲突
        com.example.fullstack.entity.Tenant tenant = tenantRepository.findByName("WebMvc测试团队")
                .orElseGet(() -> tenantRepository.save(
                        com.example.fullstack.entity.Tenant.builder().name("WebMvc测试团队").build()));

        Role userRole = roleRepository.save(Role.builder()
                .name("ROLE_USER")
                .description("Regular user")
                .build());

        userRepository.save(User.builder()
                .displayName("testuser")
                .email("test@example.com")
                .password(passwordEncoder.encode("password123"))
                .enabled(true)
                .tenant(tenant)
                .roles(Set.of(userRole))
                .build());
    }

    @Test
    void register_shouldReturnAuthResponse_whenValidRequest() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("newuser")
                .email("new@example.com")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void register_shouldReturnConflict_whenEmailAlreadyExists() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("anotheruser")
                .email("test@example.com")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void login_shouldReturnAuthResponse_whenValidCredentials() throws Exception {
        AuthRequest request = AuthRequest.builder()
                .email("test@example.com")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void login_shouldReturnUnauthorized_whenInvalidCredentials() throws Exception {
        AuthRequest request = AuthRequest.builder()
                .email("test@example.com")
                .password("wrongpassword")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshToken_shouldReturnNewAccessToken_whenValidRefreshCookie() throws Exception {
        AuthRequest loginRequest = AuthRequest.builder()
                .email("test@example.com")
                .password("password123")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = extractRefreshToken(
                loginResult.getResponse().getHeader("Set-Cookie"));

        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .cookie(new MockCookie("refresh_token", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void getCurrentUser_shouldReturnUserInfo_whenAuthenticated() throws Exception {
        AuthRequest loginRequest = AuthRequest.builder()
                .email("test@example.com")
                .password("password123")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    void getCurrentUser_shouldReturnUnauthorized_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    private String extractRefreshToken(String setCookieHeader) {
        return java.util.Arrays.stream(setCookieHeader.split(";"))
                .map(String::trim)
                .filter(part -> part.startsWith("refresh_token="))
                .findFirst()
                .map(part -> part.substring("refresh_token=".length()))
                .orElseThrow(() -> new AssertionError("Set-Cookie 中未找到 refresh_token: " + setCookieHeader));
    }
}