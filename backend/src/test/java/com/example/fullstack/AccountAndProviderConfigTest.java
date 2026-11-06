package com.example.fullstack;

import com.example.fullstack.config.RagProperties;
import com.example.fullstack.dto.AuthRequest;
import com.example.fullstack.dto.ProviderConfigRequest;
import com.example.fullstack.entity.Role;
import com.example.fullstack.entity.Tenant;
import com.example.fullstack.entity.User;
import com.example.fullstack.repository.AppSettingRepository;
import com.example.fullstack.repository.RefreshTokenRepository;
import com.example.fullstack.repository.RoleRepository;
import com.example.fullstack.repository.TenantRepository;
import com.example.fullstack.repository.UserRepository;
import com.example.fullstack.service.ProviderConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P1 交付项集成测试：自助改密 + RefreshToken 哈希存储 + PUT /admin/providers 运行时配置。
 * Provider 测试结束后恢复原配置，避免污染同上下文的其他测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountAndProviderConfigTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private AppSettingRepository appSettingRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private RagProperties ragProperties;

    // 记录原始 Provider 配置，测试后恢复
    private String origEmbProvider;
    private String origEmbModel;
    private Integer origEmbDimensions;
    private String origLlmProvider;
    private String origLlmModel;

    private Long adminUserId;

    @BeforeEach
    void setUp() {
        Tenant tenant = tenantRepository.findByName("账号配置测试团队")
                .orElseGet(() -> tenantRepository.save(Tenant.builder().name("账号配置测试团队").build()));

        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_ADMIN").description("系统管理员").build()));

        User admin = userRepository.findByEmail("acct-admin@example.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .displayName("acct-admin")
                        .email("acct-admin@example.com")
                        .password(passwordEncoder.encode("adminPass123"))
                        .enabled(true)
                        .tenant(tenant)
                        .roles(Set.of(adminRole))
                        .build()));
        adminUserId = admin.getId();

        origEmbProvider = ragProperties.getEmbedding().getProvider();
        origEmbModel = ragProperties.getEmbedding().getOpenai().getModel();
        origEmbDimensions = ragProperties.getEmbedding().getDimensions();
        origLlmProvider = ragProperties.getLlm().getProvider();
        origLlmModel = ragProperties.getLlm().getOpenai().getModel();
    }

    @AfterEach
    void tearDown() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        appSettingRepository.deleteAll();
        // 恢复 Provider 运行时配置，避免影响共享上下文的其他测试
        ragProperties.getEmbedding().setProvider(origEmbProvider);
        ragProperties.getEmbedding().getOpenai().setModel(origEmbModel);
        ragProperties.getEmbedding().setDimensions(origEmbDimensions);
        ragProperties.getLlm().setProvider(origLlmProvider);
        ragProperties.getLlm().getOpenai().setModel(origLlmModel);
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                AuthRequest.builder().email(email).password(password).build())))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private String extractRefreshToken(MvcResult loginResult) {
        String setCookie = loginResult.getResponse().getHeader("Set-Cookie");
        return java.util.Arrays.stream(setCookie.split(";"))
                .map(String::trim)
                .filter(part -> part.startsWith("refresh_token="))
                .findFirst()
                .map(part -> part.substring("refresh_token=".length()))
                .orElseThrow();
    }

    // ---------- 自助修改密码 ----------

    @Test
    void changePassword_shouldRevokeRefreshTokens_whenCorrectOldPassword() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                AuthRequest.builder().email("acct-admin@example.com").password("adminPass123").build())))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();
        String refreshToken = extractRefreshToken(loginResult);

        mockMvc.perform(put("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "oldPassword", "adminPass123",
                                "newPassword", "newSecure456"))))
                .andExpect(status().isNoContent());

        // 改密后旧 RefreshToken 必须失效（AUTH-07 闭环）
        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .cookie(new MockCookie("refresh_token", refreshToken)))
                .andExpect(status().isUnauthorized());

        // 新密码可登录、旧密码不可登录
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                AuthRequest.builder().email("acct-admin@example.com").password("newSecure456").build())))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                AuthRequest.builder().email("acct-admin@example.com").password("adminPass123").build())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_shouldReturn400_whenWrongOldPassword() throws Exception {
        String accessToken = login("acct-admin@example.com", "adminPass123");

        mockMvc.perform(put("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "oldPassword", "wrongOld!",
                                "newPassword", "newSecure456"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePassword_shouldRequireAuthentication() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "oldPassword", "adminPass123",
                                "newPassword", "newSecure456"))))
                .andExpect(status().isUnauthorized());
    }

    // ---------- RefreshToken 哈希存储（静态加密加固） ----------

    @Test
    void refreshToken_shouldBeStoredHashed_notPlaintext() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                AuthRequest.builder().email("acct-admin@example.com").password("adminPass123").build())))
                .andExpect(status().isOk())
                .andReturn();
        String rawToken = extractRefreshToken(loginResult);

        assertFalse(refreshTokenRepository.findAll().isEmpty());
        String stored = refreshTokenRepository.findAll().get(0).getToken();

        assertNotEquals(rawToken, stored, "数据库中不得存令牌原文");
        assertTrue(stored.matches("[0-9a-f]{64}"), "应为 SHA-256 的 64 位十六进制哈希，实际: " + stored);

        // 用原始令牌仍可正常刷新（哈希可查）
        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .cookie(new MockCookie("refresh_token", rawToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void accessToken_mustNotBeUsedAsRefreshToken() throws Exception {
        // 防止 7 天 RefreshToken 混用为 API 凭证（反向：access 类型不可走刷新端点）
        String accessToken = login("acct-admin@example.com", "adminPass123");

        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .cookie(new MockCookie("refresh_token", accessToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshToken_replay_shouldRevokeWholeFamily() throws Exception {
        // 重用检测：已轮换令牌被重放 → 该用户全部刷新令牌被撤销（令牌家族保护）
        MvcResult first = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                AuthRequest.builder().email("acct-admin@example.com").password("adminPass123").build())))
                .andExpect(status().isOk())
                .andReturn();
        String token1 = extractRefreshToken(first);

        MvcResult second = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                AuthRequest.builder().email("acct-admin@example.com").password("adminPass123").build())))
                .andExpect(status().isOk())
                .andReturn();
        String token2 = extractRefreshToken(second);

        // 正常轮换 token1
        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .cookie(new MockCookie("refresh_token", token1)))
                .andExpect(status().isOk());
        // 重放已轮换的 token1 → 401，且家族内全部令牌（含 token2）被撤销
        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .cookie(new MockCookie("refresh_token", token1)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .cookie(new MockCookie("refresh_token", token2)))
                .andExpect(status().isUnauthorized());
        assertTrue(refreshTokenRepository.findByUser(
                        userRepository.findByEmail("acct-admin@example.com").orElseThrow())
                .stream().allMatch(t -> Boolean.TRUE.equals(t.getRevoked())),
                "重放后该用户所有刷新令牌都应被撤销");
    }

    // ---------- PUT /admin/providers（SYS-03 运行时配置） ----------

    @Test
    void updateProviders_shouldApplyAndFlagReindex_whenEmbeddingModelChanges() throws Exception {
        String accessToken = login("acct-admin@example.com", "adminPass123");

        mockMvc.perform(put("/api/v1/admin/providers")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ProviderConfigRequest.builder()
                                .embedding(ProviderConfigRequest.EmbeddingConfig.builder()
                                        .model("text-embedding-3-large").build())
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reindexRequired").value(true))
                .andExpect(jsonPath("$.config.embedding.model").value("text-embedding-3-large"));

        assertEquals("text-embedding-3-large", ragProperties.getEmbedding().getOpenai().getModel());
    }

    @Test
    void updateProviders_shouldNotFlagReindex_whenLlmOnlyChange() throws Exception {
        String accessToken = login("acct-admin@example.com", "adminPass123");

        mockMvc.perform(put("/api/v1/admin/providers")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ProviderConfigRequest.builder()
                                .llm(ProviderConfigRequest.LlmConfig.builder()
                                        .model("gpt-4o").build())
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reindexRequired").value(false))
                .andExpect(jsonPath("$.config.llm.model").value("gpt-4o"));

        assertEquals("gpt-4o", ragProperties.getLlm().getOpenai().getModel());
    }

    @Test
    void updateProviders_shouldRejectInvalidProvider() throws Exception {
        String accessToken = login("acct-admin@example.com", "adminPass123");

        mockMvc.perform(put("/api/v1/admin/providers")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ProviderConfigRequest.builder()
                                .llm(ProviderConfigRequest.LlmConfig.builder()
                                        .provider("not-a-provider").build())
                                .build())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProviders_shouldRejectOpenaiWithoutApiKey() throws Exception {
        String accessToken = login("acct-admin@example.com", "adminPass123");
        // h2 测试环境 LLM apiKey 为空：无密钥切 openai 必须被拒绝
        if (ragProperties.getLlm().getOpenai().getApiKey() == null
                || ragProperties.getLlm().getOpenai().getApiKey().isBlank()) {
            mockMvc.perform(put("/api/v1/admin/providers")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(ProviderConfigRequest.builder()
                                    .llm(ProviderConfigRequest.LlmConfig.builder()
                                            .provider("openai").build())
                                    .build())))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void updateProviders_shouldReturn403_forNonAdmin() throws Exception {
        Tenant tenant = tenantRepository.findByName("账号配置测试团队").orElseThrow();
        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_USER").description("普通成员").build()));
        userRepository.save(User.builder()
                .displayName("acct-member")
                .email("acct-member@example.com")
                .password(passwordEncoder.encode("memberPass123"))
                .enabled(true)
                .tenant(tenant)
                .roles(Set.of(userRole))
                .build());
        String accessToken = login("acct-member@example.com", "memberPass123");

        mockMvc.perform(put("/api/v1/admin/providers")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ProviderConfigRequest.builder()
                                .llm(ProviderConfigRequest.LlmConfig.builder().model("gpt-4o").build())
                                .build())))
                .andExpect(status().isForbidden());
    }

    @Test
    void getProviders_shouldReturnMaskedKeys() throws Exception {
        String accessToken = login("acct-admin@example.com", "adminPass123");

        // 先写入一个已知 apiKey，再验证 GET 响应中只有脱敏值、绝无原文
        mockMvc.perform(put("/api/v1/admin/providers")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ProviderConfigRequest.builder()
                                .llm(ProviderConfigRequest.LlmConfig.builder()
                                        .apiKey("sk-secret-value-123456789").build())
                                .build())))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/v1/admin/providers")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embedding.provider").exists())
                .andExpect(jsonPath("$.rerankerEnabled").exists())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("sk-secret-value-123456789"), "GET 响应不得包含 apiKey 原文");
        assertTrue(body.contains("sk-s****6789") || body.contains("****"), "应返回脱敏后的密钥");
    }
}
