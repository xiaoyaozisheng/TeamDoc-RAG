# TeamDoc RAG（Spring Boot + React）

**面向小团队的轻量级知识库 + RAG 智能问答助手**：资料可管、检索可准、回答可溯源、权限不穿透、私有化可部署。

团队成员上传文档后，系统自动解析、切片、向量化；成员用自然语言提问，系统基于有权限的文档片段回答，并给出可点击的引用来源；资料不足时明确拒答。

| 文档 | 内容 | 读者 |
|------|------|------|
| [使用手册](TeamDoc-RAG-使用手册.md) | 安装部署、功能操作、配置参考、FAQ、运维 | 使用者 / 运维 |
| 本文档（README） | 技术总览、架构、API、开发规范 | 开发者 |
| [产品需求文档](TeamDoc-RAG-PRD.md) | 需求定义、验收标准、交付状态对照 | 产品 / 项目管理 |

---

## 技术栈

### 后端
- **框架**: Spring Boot 3.2.5
- **数据库**: H2 文件模式 (开发/测试) / **PostgreSQL + pgvector**（生产/RAG 底座）
- **安全**: Spring Security + JWT + RBAC（方法级鉴权）+ RLS 租户兜底
- **文档解析**: Apache PDFBox（PDF 按页）/ Apache POI（DOCX）
- **RAG**: 父子块切片 → Embedding → 混合检索（向量 + 全文 + RRF）→ 证据约束问答
- **API文档**: springdoc-openapi 2.3.0 (Swagger UI)
- **观测**: Spring Boot Actuator（/actuator/health）
- **构建工具**: Maven
- **工具**: Lombok, Jakarta Validation

### 前端
- **框架**: React 18 + TypeScript
- **构建工具**: Vite 5
- **路由**: React Router 6
- **样式**: TailwindCSS 3（边距/颜色）+ **Ant Design 6**（表格/表单组件）
- **HTTP客户端**: Axios（含Token自动刷新）
- **图表**: ECharts 6 + echarts-for-react（答辩大屏）

---

## 项目结构

```
fullstack-template/
├── backend/                          # Spring Boot 后端
│   ├── src/main/java/com/example/fullstack/
│   │   ├── controller/               # REST API 控制器
│   │   │   ├── AuthController.java   #   认证接口（RefreshToken 经 HttpOnly Cookie 下发）
│   │   │   ├── UserController.java   #   用户管理接口
│   │   │   ├── RoleController.java   #   角色管理接口
│   │   │   └── PermissionController.java  # 权限管理接口
│   │   ├── service/                  # 业务逻辑层
│   │   ├── repository/               # 数据访问层
│   │   ├── entity/                   # JPA 实体（仅业务表）
│   │   ├── dto/                      # 数据传输对象
│   │   ├── config/                   # 配置类
│   │   │   ├── SecurityConfig.java   #   Spring Security（无状态，CSRF 显式禁用）
│   │   │   ├── JwtProperties.java    #   JWT 配置属性
│   │   │   ├── JpaAuditingConfig.java #   JPA 审计开关
│   │   │   ├── OpenApiConfig.java    #   Swagger 配置
│   │   │   └── DataInitializer.java  #   数据初始化器（幂等）
│   │   ├── security/                 # 安全组件
│   │   │   ├── JwtAuthenticationFilter.java
│   │   │   ├── CustomUserDetailsService.java
│   │   │   ├── TenantContextHolder.java     # 租户上下文（RLS 用）
│   │   │   └── TenantRlsFilter.java
│   │   ├── rag/                      # RAG 链路
│   │   │   ├── embedding/            # EmbeddingProvider 适配层（hashing 本地 / openai 可换）
│   │   │   ├── ingestion/            # 解析 → 父子切片 → 向量化 → 入库（含 Worker）
│   │   │   ├── search/               # 向量 + 全文双路召回、RRF 融合、检索日志
│   │   │   ├── vector/               # pgvector 读写（postgres Profile）
│   │   │   ├── chat/                 # 证据约束问答：引用溯源 + 拒答
│   │   │   ├── llm/                  # LlmProvider 适配层（extractive 离线兜底 / openai）
│   │   │   └── eval/                 # 黄金评估集 + 批量评估（Recall@5 / 拒答率）
│   │   ├── exception/                # 全局异常处理
│   │   └── util/
│   │       └── JwtUtil.java          # JWT 工具类
│   ├── src/main/resources/
│   │   ├── application.yml           # 主配置（h2 / mysql / postgres / prod 多 Profile）
│   │   └── db/postgres/
│   │       ├── 001_vector_store.sql          # pgvector 建表 + HNSW（initdb 自动执行）
│   │       └── 002_fulltext_and_rls.sql      # 全文索引 + 租户 RLS（应用建表后手动执行）
│   ├── src/test/java/                # 单元 & 集成测试
│   └── pom.xml
├── frontend/                         # React 前端
│   ├── src/
│   │   ├── components/               # 通用组件
│   │   ├── pages/                    # 页面组件
│   │   ├── services/                 # API 服务层
│   │   ├── hooks/                    # 自定义 Hooks
│   │   ├── utils/                    # 工具函数（storage / axios 拦截器）
│   │   └── types/                    # TypeScript 类型
│   └── ...
└── README.md
```

---

## 快速开始

> 完整的部署步骤、功能操作说明、配置项详解与常见问题，见 **[使用手册](TeamDoc-RAG-使用手册.md)**。

### 后端启动

```bash
cd backend
mvn spring-boot:run     # 默认使用 h2 Profile
```

服务将在 `http://localhost:8080` 启动。

> H2 为**文件模式**（`./data/example_db`），数据不随服务重启丢失，适合开发和演示。
> 使用 MySQL：`mvn spring-boot:run -Dspring-boot.run.profiles=mysql`
> 使用 PostgreSQL（RAG 底座）：`mvn spring-boot:run -Dspring-boot.run.profiles=postgres`

### 前端启动

```bash
cd frontend
npm install
npm run dev
```

前端将在 `http://localhost:5173` 启动（已配置代理到后端 8080）。

---

## API 接口

### 认证接口（公开）

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/v1/auth/register` | 用户注册 |
| POST | `/api/v1/auth/login` | 用户登录 |
| POST | `/api/v1/auth/refresh-token` | 刷新访问令牌（依赖 `refresh_token` Cookie） |
| POST | `/api/v1/auth/logout` | 撤销刷新令牌并清除 Cookie（登出） |
| GET  | `/api/v1/auth/me` | 获取当前登录用户信息 |

> **RefreshToken 不在响应体中出现**：登录/注册/刷新通过 `Set-Cookie` 下发 `refresh_token`
> （`HttpOnly` + `Secure`（可配）+ `SameSite=Strict`，`Path=/api/v1/auth`），JavaScript 无法读取。
> 数据库仅存 RefreshToken 的 **SHA-256 哈希**（防拖库重放）；令牌携带 `type` claim，
> RefreshToken 不可当作 API 凭证使用；**已轮换令牌被重放会触发家族撤销**（该用户全部刷新令牌作废）。

### 租户管理（tenant:manage）

| 方法 | 路径 | 描述 |
|------|------|------|
| GET / POST / PUT | `/api/v1/tenants` | 租户列表 / 创建 / 更新 |
| PATCH | `/api/v1/tenants/{id}/disable` | 停用租户 |
| PATCH | `/api/v1/tenants/{id}/enable` | 启用（恢复）租户 |

### 文档管理（document:*）

| 方法 | 路径 | 权限 | 描述 |
|------|------|------|------|
| POST | `/api/v1/documents` | `document:create` | 上传（PDF/MD/TXT/DOCX ≤20MB），异步入库 |
| GET | `/api/v1/documents` | `document:read` | 本团队文档列表（含解析状态） |
| GET | `/api/v1/documents/{id}` | `document:read` | 文档详情 |
| GET | `/api/v1/documents/{id}/download` | `document:read` | 下载（权限校验） |
| DELETE | `/api/v1/documents/{id}` | `document:delete` | 软删 + 切块/向量同步清理 |
| POST | `/api/v1/documents/{id}/reindex` | `document:update` | 重新入库（失败重试） |

### 检索与问答（chat:ask）

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/v1/retrieval/search` | 混合检索调试：返回片段、文档名、页码、双路得分与 RRF 得分 |
| POST | `/api/v1/chat/ask` | 问答（JSON）：带引用编号与拒答 |
| POST | `/api/v1/chat/ask/stream` | 问答（SSE 流式）：event=answer/citations/done |
| GET | `/api/v1/chat/history` | 当前用户的问答历史（倒序分页） |
| POST | `/api/v1/chat/feedback` | 回答反馈：USEFUL / USELESS / WRONG_CITATION |

### 评估与管理（eval:manage / ADMIN）

| 方法 | 路径 | 描述 |
|------|------|------|
| GET / POST / DELETE | `/api/v1/eval/questions` | 黄金评估集管理（含负样本） |
| POST | `/api/v1/eval/runs` | 批量评估：Recall@5 + 负样本拒答准确率 |
| GET | `/api/v1/eval/runs` | 评估历史（结果可对比） |
| GET | `/api/v1/admin/logs/retrieval` | 检索日志（按租户/时间筛选） |
| GET | `/api/v1/admin/logs/audit` | 审计日志（登录/文档删除等） |
| GET | `/api/v1/admin/providers` | Embedding/LLM Provider 配置（密钥脱敏） |
| PUT | `/api/v1/admin/providers` | 运行时更新 Provider 配置与 Reranker 开关（持久化 + 热生效；Embedding 侧变更返回 reindexRequired） |

> 所有业务接口的 `tenant_id` 均取自认证上下文（JWT → 用户 → 租户），客户端传入的租户 ID 不作为过滤依据。

### 用户管理接口

| 方法 | 路径 | 权限 | 描述 |
|------|------|------|------|
| GET  | `/api/v1/users/me` | 需认证 | 获取当前用户资料 |
| PUT  | `/api/v1/users/me/password` | 需认证 | **自助修改密码**（验证原密码；成功后撤销全部刷新令牌，所有设备重新登录） |
| GET  | `/api/v1/users` | `admin:read` 或 ADMIN | 获取用户列表 |
| GET  | `/api/v1/users/{id}` | `user:read`、ADMIN 或本人 | 根据ID获取用户（防水平越权；跨租户返回 404） |
| PUT  | `/api/v1/users/{id}` | `user:update` 或 ADMIN | 更新用户信息 |
| DELETE | `/api/v1/users/{id}` | `user:delete` 或 ADMIN | 删除用户 |

### 角色管理接口

| 方法 | 路径 | 权限 | 描述 |
|------|------|------|------|
| GET  | `/api/v1/roles` | `role:read` 或 ADMIN | 获取角色列表 |
| GET  | `/api/v1/roles/{id}` | `role:read` 或 ADMIN | 获取角色详情 |
| POST | `/api/v1/roles` | `role:create` 或 ADMIN | 创建角色（含权限绑定） |
| PUT  | `/api/v1/roles/{id}` | `role:update` 或 ADMIN | 更新角色 |
| DELETE | `/api/v1/roles/{id}` | `role:delete` 或 ADMIN | 删除角色 |

### 权限管理接口

| 方法 | 路径 | 权限 | 描述 |
|------|------|------|------|
| GET  | `/api/v1/permissions` | `permission:read` 或 ADMIN | 获取权限列表 |
| GET  | `/api/v1/permissions/{id}` | `permission:read` 或 ADMIN | 获取权限详情 |
| POST | `/api/v1/permissions` | `permission:create` 或 ADMIN | 创建权限 |
| PUT  | `/api/v1/permissions/{id}` | `permission:update` 或 ADMIN | 更新权限 |
| DELETE | `/api/v1/permissions/{id}` | `permission:delete` 或 ADMIN | 删除权限 |

---

## RBAC 权限模型

采用 **RBAC（Role-Based Access Control）** 权限模型，用户 ↔ 角色 ↔ 权限。
角色/权限的读、写、删操作权限**互相独立**：普通角色可以只被授予 `role:read` 而不授予 `role:create`/`role:delete`。

### 预定义角色

| 角色 | 描述 |
|------|------|
| **ROLE_ADMIN** | 系统管理员，拥有全部权限（接口层另有 `hasRole('ADMIN')` 兜底） |
| **ROLE_USER** | 普通成员：`user:read` + `document:read/create` + `chat:ask` |

### 预定义权限

| 权限标识 | 资源 | 操作 | 描述 |
|----------|------|------|------|
| `user:read` | user | read | 查看用户信息 |
| `user:create` | user | create | 创建用户 |
| `user:update` | user | update | 更新用户 |
| `user:delete` | user | delete | 删除用户 |
| `role:read` | role | read | 查看角色 |
| `role:create` | role | create | 创建角色 |
| `role:update` | role | update | 更新角色 |
| `role:delete` | role | delete | 删除角色 |
| `permission:read` | permission | read | 查看权限 |
| `permission:create` | permission | create | 创建权限 |
| `permission:update` | permission | update | 更新权限 |
| `permission:delete` | permission | delete | 删除权限 |
| `document:read` | document | read | 查看/下载文档 |
| `document:create` | document | create | 上传文档 |
| `document:update` | document | update | 重新入库 |
| `document:delete` | document | delete | 删除文档 |
| `chat:ask` | chat | ask | 知识库检索与问答 |
| `eval:manage` | eval | manage | 管理评估集与评估任务 |
| `tenant:manage` | tenant | manage | 管理租户 |
| `admin:read` | admin | read | 查看管理信息 |

### 预定义角色

- **ROLE_ADMIN**（系统管理员）：全部权限
- **ROLE_USER**（普通成员）：`user:read` + `document:read/create` + `chat:ask`

### 默认账户

| 账户 | 邮箱 | 密码 | 角色 |
|------|------|------|------|
| 管理员 | `admin@example.com` | `admin123` | ROLE_ADMIN |
| 普通用户 | `user@example.com` | `user123` | ROLE_USER |

> 默认账户由 `DataInitializer` 在启动时**幂等**创建：权限、角色按名称补齐，已存在的数据不会被覆盖；生产环境务必修改默认密码。

---

## 安全特性

### JWT 认证
- **无状态**：服务端不存储会话，扩展性强
- **双令牌机制**：
  - **AccessToken**：短期有效（默认 **15 分钟**），用于 API 鉴权。JWT 无法从服务端单方面作废，有效期必须短以压缩泄露后的攻击窗口
  - **RefreshToken**：7 天有效，持久化存储于数据库（**仅存 SHA-256 哈希**），**只通过 HttpOnly Cookie 传输**
- **类型隔离**：令牌携带 `type` claim（access/refresh），RefreshToken 不可当作 API 凭证使用，反之亦然
- **令牌撤销**：刷新令牌时旧令牌自动标记为 `revoked=true`（轮换）
- **重用检测**：已轮换的 RefreshToken 被重放 → 立即撤销该用户全部刷新令牌并写入审计日志（家族保护）
- **密钥管控**：postgres/prod Profile 下 JWT_SECRET 缺失或过弱（<32 字符）**拒绝启动**；开发 Profile 使用默认密钥并告警
- **并发控制**：每个用户最多 5 个有效 RefreshToken，超出自动淘汰最早的一个

### Token 存储策略（防 XSS）
- **AccessToken**：前端保存在 `sessionStorage`（标签页关闭即失效）
- **RefreshToken**：`HttpOnly` + `Secure`（可由 `COOKIE_SECURE` 控制）+ `SameSite=Strict` Cookie，`Path=/api/v1/auth`，JavaScript 完全不可读
- 刷新/登出请求依赖浏览器自动携带 Cookie，前端不接触 RefreshToken

### 访问控制
- **方法级权限**：`@PreAuthorize` 逐操作声明（`role:create` ≠ `role:read`），`@EnableMethodSecurity` 已开启
- **水平越权防护**：`GET /users/{id}` 仅允许本人、`user:read` 权限或 ADMIN 访问；**user:read 持有者只能查看本租户用户**（跨租户 404，不泄露存在性）
- **内置角色保护**：ROLE_ADMIN / ROLE_USER 不可改名或删除
- **CSRF**：无状态 JWT API **显式禁用** CSRF（令牌在 `Authorization` 头中，浏览器不会自动携带，无 CSRF 攻击面）。RefreshToken Cookie 使用 `SameSite=Strict` 提供跨站防护，因此刷新接口无需额外 CSRF token
- **统一异常处理**：全局 `@RestControllerAdvice` 处理所有异常；JWT 过期/伪造统一 401（过滤器捕获，不产生 500）
- **开发端点门控**：H2 控制台与 Swagger 仅在开发 Profile 开放（`security.dev-endpoints-enabled`），postgres/prod 默认关闭

### Token 自动刷新机制（前端）

1. Axios 请求拦截器自动携带 AccessToken
2. 响应拦截器检测 401 状态码（认证接口自身的 401 除外，避免循环刷新）
3. 自动调用 `/api/v1/auth/refresh-token`（Cookie 由浏览器自动携带）获取新令牌，新的 RefreshToken 经 `Set-Cookie` 轮换
4. 并发的 401 请求排队等待刷新完成后自动重试
5. 刷新失败时清空本地状态并跳转登录页

---

## API 文档

启动后端服务后访问：

| 地址 | 说明 |
|------|------|
| `http://localhost:8080/swagger-ui.html` | Swagger UI 交互式文档 |
| `http://localhost:8080/v3/api-docs` | OpenAPI JSON 规范 |

---

## 配置说明

### 数据库切换

`application.yml` 预设四个 Profile，默认使用 **H2 文件模式**（`./data/example_db`）：

| Profile | 数据库 | 适用场景 |
|---------|--------|----------|
| `h2`（默认） | H2 文件模式 | 开发调试 |
| `mysql` | MySQL | 本地完整测试 |
| `postgres` | PostgreSQL | RAG 向量库底座 |
| `prod` | MySQL（生产配置） | 生产部署 |

### PostgreSQL + pgvector（RAG 底座）

1. 安装 PostgreSQL（16）和 pgvector 扩展，或直接使用 `docker-compose.yml`（pgvector/pgvector:pg16 镜像）
2. 初始化脚本分两步（见 `backend/src/main/resources/db/postgres/`）：
   - `001_vector_store.sql`：扩展、`vector_store` 表、HNSW 索引 —— **initdb 阶段自动执行**
   - `002_fulltext_and_rls.sql`：全文 GIN 索引 + documents/chunks/conversations 的租户 RLS 策略 —— **应用首次启动建表后手动执行**
   - **`vector(768)` 维度需与 `rag.embedding.dimensions` 一致**（bge-small-zh 为 512，bge-base-zh 为 768）
3. `mvn spring-boot:run -Dspring-boot.run.profiles=postgres`（该 Profile 自动开启 pgvector 读写与 RLS 连接注入）

### RAG 链路与 Provider 替换

```
上传 → 解析(PDFBox/POI/Markdown) → 父块(≤1200字) + 子块(≤400字,按句切) → Embedding → chunks(+pgvector)
提问 → 混合检索(向量余弦 + 全文) → RRF 融合 → 余弦低于阈值拒答 → 证据约束 Prompt → LLM → 引用溯源
```

```yaml
rag:
  embedding:
    provider: hashing        # hashing（本地确定性零依赖）| openai（兼容任意 OpenAI 风格网关）
  llm:
    provider: extractive     # extractive（离线兜底：片段内最佳句 + 拒答）| openai
  retrieval:
    reranker-enabled: false  # RET-07：重排开关；开启后对融合候选（rerank-candidates，默认20）重排取 top-k
```

Provider 配置也支持运行时修改：系统管理员在「系统管理」页面或经 `PUT /api/v1/admin/providers`
调整 Provider/模型/密钥/重排开关，配置持久化到 `app_settings` 表（重启自动恢复）、立即热生效、
密钥只回传脱敏值；Embedding 侧变更会返回 `reindexRequired=true`，需对各文档执行重新入库。

生产建议：Embedding 换 `bge-small-zh` ONNX 或云端 API（实现 `EmbeddingProvider` 接口即可，流水线零改动）；LLM 换任意 OpenAI 兼容端点。检索 Recall 未达标前不要调 Prompt——先看 `retrieval_logs`、调切片参数（`rag.chunk.*`）和检索参数（`rag.retrieval.*`）。

### Docker Compose 私有化部署

```bash
# 1) 配置环境变量（建议）
export JWT_SECRET=<至少32字符的随机串>
export DB_PASSWORD=<数据库密码>

# 2) 一键启动（PostgreSQL + 后端 + 前端）
docker compose up -d --build

# 3) 应用首次启动后执行一次 RLS 初始化
docker compose exec db psql -U teamdoc -d teamdoc \
  -f /docker-entrypoint-initdb.d/002_fulltext_and_rls.sql
#   （或把 002 脚本内容粘贴进 psql）

# 4) 访问 http://localhost（前端），默认账户 admin@example.com / admin123
```

### JWT 配置

```yaml
jwt:
  secret: your-256-bit-secret-key       # JWT 签名密钥（生产必填环境变量）
  expiration: 900000                    # AccessToken 有效期（毫秒，默认15分钟）
  refresh-expiration: 604800000         # RefreshToken 有效期（毫秒，默认7天）
  cookie-secure: false                  # RefreshToken Cookie 的 Secure 标记（postgres/prod Profile 下为 true）
```

### 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | PostgreSQL 连接信息（postgres Profile） | `localhost` / `5432` / `teamdoc` |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库凭据 | - |
| `JWT_SECRET` | JWT 密钥（**postgres/prod Profile 必填**，≥32 字符，否则拒绝启动） | - |
| `COOKIE_SECURE` | RefreshToken Cookie 的 Secure 标记（纯 HTTP 的 Compose 部署设为 false，有 TLS 的生产必须为 true） | postgres/prod 下 `true` |
| `CORS_ALLOWED_ORIGINS` | 允许的跨域来源 | `http://localhost:5173` |
| `OPENAI_API_KEY` | Embedding/LLM 云端密钥（`rag.*.provider=openai` 时） | - |
| `LLM_API_KEY` | LLM 专用密钥 | - |

---

## 开发规范

### 后端规范
- **Lombok**：使用 `@Slf4j`、`@RequiredArgsConstructor`、`@Builder` 减少样板代码
- **RESTful 设计**：遵循资源命名与 HTTP 方法语义
- **参数校验**：DTO 字段使用 `@NotBlank`、`@Email`、`@Size` 等 Validation 注解
- **统一异常**：业务异常继承 `RuntimeException`，全局处理器统一返回格式
- **权限注解**：Controller 方法使用 `@PreAuthorize` 逐操作声明访问控制
- **JPA 审计**：实体继承审计字段，由 `JpaAuditingConfig` 自动维护

### 前端规范
- **TypeScript 严格模式**：`strict: true`
- **函数式组件**：使用 Hooks 管理状态与副作用
- **API 分层**：请求逻辑统一封装在 `services/` 目录
- **类型统一**：接口类型定义在 `types/` 目录集中管理

---

## 测试策略

### 运行测试

```bash
cd backend
mvn test
```

### 测试类型

| 测试类型 | 注解 | 范围 |
|----------|------|------|
| 集成测试 | `@SpringBootTest` | 完整认证流程测试 |
| 数据层测试 | `@DataJpaTest` | Repository 复杂查询 |
| Web层测试 | `@WebMvcTest` | Controller 路径覆盖 |

---

## 测试验证

```bash
cd backend
mvn test     # 47 个测试
```

测试覆盖（对应 PRD 验收标准 11.1 + P1 交付项）：

| 测试类 | 覆盖 |
|--------|------|
| `RrfFuserTest` | RRF 融合纯函数（排名合并、TopK、空输入） |
| `LexicalRerankProviderTest` | Reranker 重排（相关片段提升、TopK、元数据保留、bigram 边界） |
| `RagFlowIntegrationTest` | 全链路：上传入库 → 混合检索 → 问答引用 → 负样本拒答 → **跨租户隔离** → 删除后不可召回 → 评估指标（Recall@5/拒答率） → 权限矩阵 403 |
| `AuthControllerTest` / `AuthControllerWebMvcTest` | 认证闭环：注册/登录/Cookie 刷新/无 Cookie 401 |
| `AccountAndProviderConfigTest` | P1 交付项：自助改密（旧密码校验/令牌撤销/新密码登录）、RefreshToken 哈希落库、令牌类型隔离、重放触发家族撤销、PUT /admin/providers（热生效/reindexRequired/校验/403/脱敏） |
| `UserRepositoryTest` | 数据层 |

> RAG 全链路测试运行在独立内存库 `jdbc:h2:mem:ragflow`，与其他测试和开发数据隔离，可重复执行。

---

## 前端认证闭环

```
用户登录 → 服务端 Set-Cookie 下发 HttpOnly refresh_token（JS 不可读）
         → 前端仅将 AccessToken 存入 sessionStorage
         → Axios 请求拦截器添加 Authorization: Bearer xxx
         → 请求成功 ✅
         → 请求 401 ❌（非认证接口）
           → Axios 响应拦截器自动调用 /refresh-token（Cookie 由浏览器自动携带）
             → 成功 → 更新 sessionStorage 中的 AccessToken（RefreshToken 经 Set-Cookie 轮换）→ 重试原请求 ✅
             → 失败 → 清空本地状态 → 重定向登录页
登出 → POST /logout 撤销服务端 RefreshToken 并清除 Cookie
```

---

## 路线图（RAG，对应 PRD 里程碑）

- [x] M0 安全修复（权限拆分、令牌有效期、Cookie 化、越权防护）
- [x] Flowable 移除（知识库场景无审批流需求）
- [x] M1 数据底座与租户（PostgreSQL/pgvector 脚本、租户模型、应用层过滤 + RLS 兜底）
- [x] M2 文档入库（PDF/MD/TXT/DOCX 解析、父子切片、Embedding、异步 Worker、失败重试、PROCESSING 卡死回收）
- [x] M3 检索闭环（向量 + 全文 + RRF、检索日志、评估集基线）
- [x] M4 问答与溯源（SSE 流式、引用标注、拒答、反馈、问答历史）
- [x] M5 评估与加固（黄金集 CRUD、批量评估、审计日志、Docker Compose）
- [x] P1 加固批（2026-11-06）：自助改密、RefreshToken SHA-256 哈希 + 类型隔离 + 重放家族撤销、Provider 运行时配置（PUT + 持久化 + 脱敏）、词面 Reranker 开关（RET-07）、JWT 密钥 fail-fast、开发端点门控、SSE 租户上下文修复、RLS 脚本 missing_ok 修复
- [ ] P2：Reranker 交叉编码器模型、多轮对话上下文记忆、SSO/OIDC、MinIO 对象存储、扫描件 OCR

