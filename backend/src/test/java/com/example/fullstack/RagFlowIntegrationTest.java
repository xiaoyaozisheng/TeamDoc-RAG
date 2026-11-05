package com.example.fullstack;

import com.example.fullstack.entity.*;
import com.example.fullstack.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * RAG 全链路集成测试（H2 Profile）：
 * 文档上传 -> 入库 -> 检索 -> 问答 -> 引用/拒答 -> 评估 -> 租户隔离 -> 权限矩阵。
 */
@SpringBootTest(properties = {
        "rag.ingestion.worker-enabled=false",
        // 独立内存库：与其他测试类的文件库隔离，保证全链路断言的确定性
        "spring.datasource.url=jdbc:h2:mem:ragflow;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RagFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private ChunkRepository chunkRepository;
    @Autowired private com.example.fullstack.rag.ingestion.IngestionService ingestionService;
    @Autowired private PasswordEncoder passwordEncoder;

    private String adminToken;
    private Long tenantBId;

    /** 公司报销制度（租户 A 的知识库内容） */
    private static final String DOC_A_CONTENT = """
            # 公司报销制度

            ## 报销审批流程
            员工报销审批共需要三级审批：部门主管审批、财务主管审批、总经理审批。
            单笔金额超过一万元的报销需要额外附上发票原件。

            ## 报销时限
            报销单必须在费用发生后 30 天内提交，逾期不予受理。

            ## 差旅标准
            员工出差住宿标准为每晚 400 元，一线城市为每晚 600 元。
            """;

    /** 租户 B 的知识库内容（用于交叉租户验证） */
    private static final String DOC_B_CONTENT = """
            # B团队保密制度
            B团队的服务器 root 密码每季度更换一次，由运维负责人保管。
            """;

    @BeforeAll
    void setUpUsers() throws Exception {
        // 自建管理员：不依赖 DataInitializer 创建的默认账户（其他测试类可能清理过 users 表）
        Tenant tenantA = tenantRepository.findByName("默认团队")
                .orElseGet(() -> tenantRepository.save(Tenant.builder().name("默认团队").build()));
        Role adminRole = roleRepository.findByName("ROLE_ADMIN").orElseThrow();
        User admin = userRepository.findByEmail("rag-admin@example.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .displayName("rag-admin")
                        .email("rag-admin@example.com")
                        .password(passwordEncoder.encode("admin123"))
                        .enabled(true)
                        .tenant(tenantA)
                        .roles(Set.of(adminRole))
                        .build()));
        adminToken = login("rag-admin@example.com", "admin123");

        Tenant tenantB = tenantRepository.findByName("隔离测试团队B")
                .orElseGet(() -> tenantRepository.save(Tenant.builder().name("隔离测试团队B").build()));
        tenantBId = tenantB.getId();
        userRepository.findByEmail("tenantb-admin@example.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .displayName("tenantb-admin")
                        .email("tenantb-admin@example.com")
                        .password(passwordEncoder.encode("password123"))
                        .enabled(true)
                        .tenant(tenantB)
                        .roles(Set.of(adminRole))
                        .build()));

        Role userRole = roleRepository.findByName("ROLE_USER").orElseThrow();
        userRepository.findByEmail("rag-member@example.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .displayName("rag-member")
                        .email("rag-member@example.com")
                        .password(passwordEncoder.encode("user123"))
                        .enabled(true)
                        .tenant(tenantA)
                        .roles(Set.of(userRole))
                        .build()));
    }

    @Test
    @Order(1)
    void uploadAndIngestDocument() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "公司报销制度.txt", MediaType.TEXT_PLAIN_VALUE,
                                DOC_A_CONTENT.getBytes("UTF-8")))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        Long documentId = objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .get("id").asLong();

        // 同步入库（reindex 入口），失败会带出错误信息
        mockMvc.perform(post("/api/v1/documents/" + documentId + "/reindex")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        assertThat(chunkRepository.findByDocumentId(documentId))
                .anyMatch(chunk -> chunk.getEmbedding() != null)
                .anyMatch(chunk -> chunk.getParentChunkId() != null);
    }

    @Test
    @Order(2)
    void hybridSearchRecallsIngestedContent() throws Exception {
        mockMvc.perform(post("/api/v1/retrieval/search")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"报销审批需要几级\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits[0].content").isNotEmpty())
                .andExpect(jsonPath("$.hits[0].documentName").value("公司报销制度.txt"));
    }

    @Test
    @Order(3)
    void askReturnsGroundedAnswerWithCitation() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/chat/ask")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"报销审批需要几级审批？\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode answer = objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(answer.get("rejected").asBoolean()).isFalse();
        assertThat(answer.get("answer").asText()).contains("三级");
        assertThat(answer.get("citations").size()).isGreaterThanOrEqualTo(1);
        assertThat(answer.get("citations").get(0).get("documentName").asText())
                .isEqualTo("公司报销制度.txt");
    }

    @Test
    @Order(4)
    void askRejectsWhenKnowledgeBaseLacksAnswer() throws Exception {
        mockMvc.perform(post("/api/v1/chat/ask")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"公司年会在哪一天举办？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rejected").value(true))
                .andExpect(jsonPath("$.answer").value("资料中未找到"));
    }

    @Test
    @Order(5)
    void crossTenantIsolationBlocksOtherTenantData() throws Exception {
        // 租户 B 上传并入库自己的文档
        String tokenB = login("tenantb-admin@example.com", "password123");
        mockMvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "B团队保密制度.txt", MediaType.TEXT_PLAIN_VALUE,
                                DOC_B_CONTENT.getBytes("UTF-8")))
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk());

        Long docBId = documentRepository.findAll().stream()
                .filter(d -> d.getTenant().getId().equals(tenantBId))
                .findFirst().orElseThrow().getId();
        ingestionService.processDocument(docBId);

        // 租户 B 用户检索报销制度（租户 A 的内容）→ 候选中绝不出现租户 A 的文档
        MvcResult searchB = mockMvc.perform(post("/api/v1/retrieval/search")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"报销审批需要几级审批\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode hitsB = objectMapper.readTree(searchB.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).get("hits");
        for (JsonNode hit : hitsB) {
            assertThat(hit.get("documentName").asText())
                    .as("跨租户数据泄漏: " + hit)
                    .isNotEqualTo("公司报销制度.txt");
        }

        // 租户 B 用户问答也绝不可能引用租户 A 的文档
        MvcResult askB = mockMvc.perform(post("/api/v1/chat/ask")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"报销审批需要几级审批？\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode answerB = objectMapper.readTree(askB.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(answerB.get("rejected").asBoolean()).isTrue();

        // 租户 A 无法看到/下载租户 B 的文档
        mockMvc.perform(get("/api/v1/documents/" + docBId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(6)
    void deletedDocumentIsNotRecallable() throws Exception {
        Long docAId = documentRepository.findAll().stream()
                .filter(d -> d.getName().equals("公司报销制度.txt") && !d.getDeleted())
                .findFirst().orElseThrow().getId();

        mockMvc.perform(post("/api/v1/retrieval/search")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"报销审批\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits[0]").exists());

        mockMvc.perform(delete("/api/v1/documents/" + docAId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/retrieval/search")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"报销审批\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits").isEmpty());
    }

    @Test
    @Order(7)
    void evalRunComputesMetrics() throws Exception {
        // 重建文档（Order 6 已删除）并入库
        mockMvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "公司报销制度.txt", MediaType.TEXT_PLAIN_VALUE,
                                DOC_A_CONTENT.getBytes("UTF-8")))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        Long docId = documentRepository.findAll().stream()
                .filter(d -> d.getName().equals("公司报销制度.txt") && !d.getDeleted())
                .findFirst().orElseThrow().getId();
        ingestionService.processDocument(docId);

        // 录入黄金评估集：2 正样本 + 1 负样本
        mockMvc.perform(post("/api/v1/eval/questions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"报销审批需要几级审批？\", \"expectedDocId\": " + docId + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/eval/questions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"住宿标准是多少元？\", \"expectedDocId\": " + docId + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/eval/questions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"公司年会定在哪一天？\", \"negative\": true}"))
                .andExpect(status().isOk());

        MvcResult runResult = mockMvc.perform(post("/api/v1/eval/runs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode metrics = objectMapper.readTree(runResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(metrics.get("questionCount").asInt()).isEqualTo(3);
        assertThat(metrics.get("recallAt5").asDouble()).isGreaterThanOrEqualTo(0.8);
        assertThat(metrics.get("rejectionAccuracy").asDouble()).isEqualTo(1.0);
    }

    @Test
    @Order(8)
    void permissionMatrixBlocksUnauthorizedAccess() throws Exception {
        String userToken = login("rag-member@example.com", "user123");

        // 普通成员不能写角色/权限（AUTH-01）
        mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"ROLE_HACK\", \"description\": \"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/permissions")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"hack:all\", \"resource\": \"hack\", \"action\": \"all\"}"))
                .andExpect(status().isForbidden());

        // 普通成员不能管理租户/评估/管理后台
        mockMvc.perform(post("/api/v1/tenants")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"非法租户\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/eval/questions")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"q\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/logs/retrieval")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        // 普通成员不能删除文档（document:delete 未授予 ROLE_USER）
        mockMvc.perform(delete("/api/v1/documents/1")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        // 普通成员可以问答（chat:ask）
        mockMvc.perform(post("/api/v1/chat/ask")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"差旅住宿标准是多少？\"}"))
                .andExpect(status().isOk());
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).get("accessToken").asText();
    }
}
