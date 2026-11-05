package com.example.fullstack;

import com.example.fullstack.dto.AuthRequest;
import com.example.fullstack.dto.RegisterRequest;
import com.example.fullstack.repository.RefreshTokenRepository;
import com.example.fullstack.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private com.example.fullstack.repository.ConversationRepository conversationRepository;

    @Autowired
    private com.example.fullstack.repository.ChunkRepository chunkRepository;

    @Autowired
    private com.example.fullstack.repository.DocumentRepository documentRepository;

    @AfterEach
    void tearDown() {
        // 先清业务数据再删用户，避免文件库残留文档的 FK 约束
        chunkRepository.deleteAll();
        documentRepository.deleteAll();
        conversationRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void testRegister() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("testuser")
                .email("test@example.com")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                // RefreshToken 只通过 HttpOnly Cookie 下发，不出现在响应体
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("refresh_token=")))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("SameSite=Strict")));
    }

    @Test
    void testLogin() throws Exception {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .username("loginuser")
                .email("login@example.com")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)));

        AuthRequest loginRequest = AuthRequest.builder()
                .email("login@example.com")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void testLoginWithInvalidCredentials() throws Exception {
        AuthRequest request = AuthRequest.builder()
                .email("nonexistent@example.com")
                .password("wrongpassword")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testRefreshToken() throws Exception {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .username("refreshtokenuser")
                .email("refresh@example.com")
                .password("password123")
                .build();

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = extractRefreshToken(
                registerResult.getResponse().getHeader("Set-Cookie"));

        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .cookie(new MockCookie("refresh_token", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void testRefreshTokenWithoutCookieIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh-token"))
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
