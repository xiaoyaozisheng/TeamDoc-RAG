# TeamDoc RAG 使用手册

> 适用版本：MVP v1.0（2026-11-03）
> 配套文档：[README.md](README.md)（技术总览）｜ [TeamDoc-RAG-PRD.md](TeamDoc-RAG-PRD.md)（产品需求）

TeamDoc RAG 是面向小团队（10–50 人）的私有化知识库 + RAG 智能问答助手：上传文档后系统自动解析、切片、向量化；成员用自然语言提问，系统基于**本团队有权限的文档片段**回答并给出可点击的引用来源；资料不足时明确拒答，不编造。

---

## 目录

1. [快速开始](#1-快速开始)
2. [登录与账户](#2-登录与账户)
3. [文档管理](#3-文档管理)
4. [知识库问答](#4-知识库问答)
5. [评估：量化检索质量](#5-评估量化检索质量)
6. [系统管理](#6-系统管理)
7. [角色与权限](#7-角色与权限)
8. [配置参考](#8-配置参考)
9. [切换生产数据库（PostgreSQL + pgvector）](#9-切换生产数据库postgresql--pgvector)
10. [切换云端 AI 模型](#10-切换云端-ai-模型)
11. [常见问题 FAQ](#11-常见问题-faq)
12. [数据存储与运维](#12-数据存储与运维)

---

## 1. 快速开始

### 方式一：开发模式（本机调试，无需 Docker）

```bash
# 后端（默认 h2 Profile，数据库与文件存储在 backend/data/ 下，开箱即用）
cd backend
mvn spring-boot:run            # http://localhost:8080

# 前端（另开终端）
cd frontend
npm install
npm run dev                    # http://localhost:5173
```

### 方式二：Docker Compose 私有化部署（推荐生产使用）

```bash
# 1) 设置必要的环境变量
export JWT_SECRET=<至少32位的随机字符串>
export DB_PASSWORD=<数据库密码>

# 2) 一键启动 PostgreSQL(pgvector) + 后端 + 前端
docker compose up -d --build

# 3) 应用首次启动后执行一次（全文索引 + 租户 RLS，见第 9 节说明）
docker compose exec db psql -U teamdoc -d teamdoc -c "$(cat backend/src/main/resources/db/postgres/002_fulltext_and_rls.sql | grep -v '^--')"

# 4) 浏览器访问 http://localhost（前端端口可用 FRONTEND_PORT 调整）
```

> 部署完成后先按第 2 节修改默认密码，再导入真实文档。

### 健康检查

| 检查项 | 方式 |
|--------|------|
| 后端存活 | `GET http://localhost:8080/actuator/health` 返回 `{"status":"UP"}` |
| 前端可访问 | 浏览器打开 `http://localhost:5173`（开发）/ `http://localhost`（Compose） |
| API 文档 | `http://localhost:8080/swagger-ui.html` |

---

## 2. 登录与账户

系统首次启动时自动创建两个默认账户：

| 账户 | 邮箱 | 密码 | 角色 |
|------|------|------|------|
| 系统管理员 | `admin@example.com` | `admin123` | ROLE_ADMIN |
| 普通成员 | `user@example.com` | `user123` | ROLE_USER |

**首次登录后请立即修改默认密码**：登录后点击右上角「**修改密码**」自助完成（需验证原密码；修改成功后所有已登录设备自动退出，需重新登录）。管理员也可通过 `PUT /api/v1/users/{id}`（请求体带 `password` 字段，需 `user:update` 权限）为任何账户重置密码。

登录流程说明：

- 登录后前端持有 15 分钟有效的 AccessToken（存 sessionStorage，关闭标签页即失效）；
- RefreshToken（7 天）保存在 HttpOnly Cookie 中，JavaScript 无法读取；
- 页面停留超过 15 分钟后发起请求，系统会自动刷新令牌，无需重新登录；
- 点击"退出登录"会撤销服务端 RefreshToken，之后即使 Cookie 泄露也无法刷新。

---

## 3. 文档管理

入口：顶部导航 **文档管理**。

### 3.1 上传文档

点击右上角 **上传文档**，支持多选。限制：

| 项目 | 限制 |
|------|------|
| 格式 | PDF、Markdown（.md）、TXT、DOCX |
| 大小 | 单文件 ≤ 20MB |
| 租户 | 文档归属于你所在的团队，其他团队不可见 |

上传成功后文档进入 **异步入库** 流水线：解析 → 切片（按标题/段落切父块，再按句切子块）→ 向量化 → 入库。**上传立即返回，不必等待处理完成。**

### 3.2 状态说明

| 状态 | 含义 | 需要做什么 |
|------|------|------------|
| 待处理 PENDING | 已排队 | 无需操作，Worker 约每 3 秒扫描一次 |
| 处理中 PROCESSING | 正在解析/向量化 | 无需操作 |
| 已入库 SUCCESS | 可被检索和问答 | — |
| 失败 FAILED | 解析或入库出错（悬停可见原因） | 点 **重新入库**；自动重试 3 次仍失败后需人工介入 |

### 3.3 其他操作

- **下载**：下载原始文件（校验文档级权限）；
- **重新入库**：对已入库/失败的文档重建切块与向量（幂等，不会产生重复向量）；
- **删除**：软删除 + 立即清除该文档全部切块与向量，**删除后立即不再被检索召回**。

> 切片参数（父块 ≤1200 字、子块 ≤400 字）由 `rag.chunk.*` 配置控制，见第 8 节。

---

## 4. 知识库问答

入口：顶部导航 **知识库问答**。

### 4.1 提问

输入问题（如"报销审批需要几级？"），回答以流式方式逐段输出。回答规则：

1. **仅基于本团队文档片段回答**，每个结论标注引用编号 `[1]`、`[2]`；
2. **引用可点击**，显示来源文档名与页码；
3. **资料中未找到**：知识库里没有足够依据时明确拒答，不编造。看到拒答说明：文档未上传/未入库成功，或问题超出已上传资料范围。

### 4.2 反馈

每条非拒答的回答下方有三个反馈按钮，用于沉淀质量数据：

| 按钮 | 含义 | 用途 |
|------|------|------|
| 有用 | 回答正确且引用得当 | 正向样本 |
| 无用 | 回答没帮上忙 | 触发检索/切片排查 |
| 引用错误 | 结论与引用出处对不上 | 优先排查（可能需要切换更强的 LLM） |

### 4.3 问答有效性的三个前提

1. 文档已上传且状态为 **已入库**；
2. 问题的答案确实存在于你团队的文档中；
3. 检索质量达标——用第 5 节的评估功能量化，而不是凭感觉。

---

## 5. 评估：量化检索质量

入口：顶部导航 **评估**（需 `eval:manage` 权限）。

这是本系统最重要的运营工具。**RAG 答错的根因大多在检索与切片，不在生成**——修改切片/检索参数后必须回归评估，Recall@5 未达 0.80 前不要调 Prompt。

### 5.1 建立黄金评估集

点击 **添加** 录入问题，建议 30–50 题，并包含 3–5 道"知识库无答案"的负样本：

| 类型 | 填写方式 | 示例 |
|------|----------|------|
| 正样本 | 填问题 + 期望命中的文档 ID | 问题"产品X的型号编码是什么？"，期望文档 = 《产品手册》的 ID |
| 负样本 | 勾选"负样本"，不填文档 | 问题"公司年会定在哪一天？"（知识库中无答案） |

文档 ID 可在文档管理页面的接口返回中查看（`GET /api/v1/documents` 的 `id` 字段）。

### 5.2 批量评估与指标解读

点击 **批量评估**，系统逐题执行检索并计算：

| 指标 | 含义 | 目标 |
|------|------|------|
| **Recall@5** | 正样本期望文档出现在前 5 条检索结果中的比例 | ≥ 0.80 |
| **拒答准确率** | 负样本被正确拒答的比例 | ≥ 0.90 |

每次评估结果保存在 **评估历史** 中，可对比调整前后的变化。

### 5.3 指标不达标时怎么排查

| 症状 | 优先排查 |
|------|----------|
| Recall@5 低 | 文档是否入库成功；切片是否把表格/条款切碎（调 `rag.chunk.max-child-chars`）；换更强的 Embedding（第 10 节） |
| 拒答准确率低（该拒的不拒） | 调高 `rag.retrieval.min-relevance`（默认 0.35） |
| 该答的不答（误拒答） | 调低 `rag.retrieval.min-relevance`；确认相关文档已入库 |

> `min-relevance` 阈值与 Embedding 模型相关：更换模型后必须重新校准。

---

## 6. 系统管理

以下功能仅系统管理员（ADMIN）可用。

### 6.1 用户 / 角色 / 权限

- **用户管理** `GET/PUT/DELETE /api/v1/users`：列表、更新、删除；`GET /users/{id}` 仅本人、`user:read` 权限者或管理员可访问；
- **角色管理** `/api/v1/roles` 与 **权限管理** `/api/v1/permissions`：读、写、删操作权限互相独立，可给自定义角色只授予 `role:read` 而不给 `role:create`；
- 普通成员调用任何写接口返回 403。

### 6.2 检索日志（排查"为什么答错"）

`GET /api/v1/admin/logs/retrieval`：记录每次检索的查询、候选数、耗时与融合后候选列表。问答不满意时，先看这里命中了什么、没命中什么。

### 6.3 审计日志

`GET /api/v1/admin/logs/audit`：记录登录、文档删除等关键动作（操作者、租户、时间）。

### 6.4 AI Provider 配置（页面可视化）

前端「**系统管理**」页（仅 ROLE_ADMIN 可见）或 API `GET/PUT /api/v1/admin/providers`：

- **查看**：当前 Embedding/LLM 供应商、模型、Base URL，API Key 脱敏展示（只显示前 4 位 + `****`，原文永不回传）；
- **修改**：`PUT` 直接热生效——供应商（hashing/extractive ↔ openai）、模型、Base URL、API Key（留空保持不变）、超时、Reranker 开关；
- **持久化**：配置存入 `app_settings` 表，服务重启后自动恢复；
- **切换 Embedding 模型**：响应返回 `reindexRequired=true`，请到「文档管理」对各文档执行"重新入库"，否则旧向量与新模型不可比，检索质量会下降。

### 6.5 租户管理

`/api/v1/tenants`：创建/停用团队空间（需 `tenant:manage`）。新注册用户默认进入"默认团队"，由管理员划入正式团队。**所有业务数据（文档、切块、问答）按租户隔离，检索/问答/下载永远只返回本团队数据。**

---

## 7. 角色与权限

| 能力 | 系统管理员 ROLE_ADMIN | 普通成员 ROLE_USER |
|------|:---:|:---:|
| 全局用户/角色/权限管理 | ✅ | ❌ |
| 租户创建与管理 | ✅ | ❌ |
| 文档查看 / 下载 | ✅（全部团队） | ✅（本团队） |
| 文档上传 | ✅ | ✅ |
| 文档重新入库 | ✅ | ❌（`document:update` 未授予） |
| 文档删除 | ✅ | ❌（`document:delete` 未授予） |
| 检索与问答 | ✅ | ✅ |
| 评估集管理与批量评估 | ✅ | ❌ |
| 检索/审计日志 | ✅ | ❌ |

自定义角色：管理员可在角色管理中创建新角色并勾选任意权限组合（20 项细粒度权限，见 README 权限表），例如给"团队管理员"授予本团队需要的 `document:*` 与 `eval:manage`。

---

## 8. 配置参考

配置文件：`backend/src/main/resources/application.yml`。Profile 切换：

```bash
mvn spring-boot:run                               # h2（默认，开发）
mvn spring-boot:run -Dspring-boot.run.profiles=postgres   # PostgreSQL + pgvector
```

### RAG 核心参数

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `rag.storage.base-dir` | `./data/files` | 原始文件存储目录（本地卷） |
| `rag.embedding.provider` | `hashing` | `hashing`（本地确定性，零依赖）/ `openai` |
| `rag.embedding.dimensions` | `768` | 向量维度，**必须与 pgvector 表的 `vector(768)` 一致** |
| `rag.embedding.openai.*` | — | `base-url` / `model` / `api-key`（OpenAI 兼容网关均可） |
| `rag.llm.provider` | `extractive` | `extractive`（离线兜底）/ `openai` |
| `rag.llm.openai.*` | — | `base-url` / `model` / `api-key` / `timeout-seconds` |
| `rag.chunk.max-parent-chars` | `1200` | 父块上限（供 LLM 的完整章节上下文） |
| `rag.chunk.max-child-chars` | `400` | 子块上限（供检索的细粒度片段） |
| `rag.retrieval.top-k` | `5` | 融合后返回/引用的片段数 |
| `rag.retrieval.rrf-k` | `60` | RRF 常数，一般不动 |
| `rag.retrieval.min-relevance` | `0.35` | 证据充分性阈值（向量余弦），低于即拒答 |
| `rag.ingestion.max-retries` | `3` | FAILED 自动重试上限 |
| `rag.ingestion.scan-interval-ms` | `3000` | Worker 扫描间隔 |
| `rag.pgvector.enabled` | `false` | postgres Profile 自动置 true |
| `rag.rls.enabled` | `false` | postgres Profile 自动置 true |

### 其他关键配置

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `jwt.expiration` | `900000` | AccessToken 15 分钟 |
| `jwt.refresh-expiration` | `604800000` | RefreshToken 7 天 |
| `jwt.cookie-secure` | h2: `false` / postgres、prod: `true` | 生产 HTTPS 环境必须 true |
| `jwt.secret` | 开发默认值 | **生产必须用 `JWT_SECRET` 环境变量注入**（≥32 字符） |
| `cors.allowed-origins` | `http://localhost:5173,...` | 前端地址 |
| `spring.servlet.multipart.max-file-size` | `20MB` | 上传上限 |

---

## 9. 切换生产数据库（PostgreSQL + pgvector）

开发模式用 H2 文件库即可完整体验全部功能（向量检索在应用内存中执行）。生产/大规模数据切换 PostgreSQL：

1. 准备 PostgreSQL 16 + pgvector 扩展（Compose 已内置 `pgvector/pgvector:pg16` 镜像）；
2. 初始化脚本分两步（`backend/src/main/resources/db/postgres/`）：
   - `001_vector_store.sql`：扩展、`vector_store` 表、HNSW 索引——Compose 部署时由数据库初始化机制**自动执行**；手动部署时在**建库后、应用首次启动前**执行；
   - `002_fulltext_and_rls.sql`：全文 GIN 索引 + documents/chunks/conversations 的租户 RLS 策略——**必须等应用首次启动（JPA 建表完成）后执行一次**；
3. 以 postgres Profile 启动：`mvn spring-boot:run -Dspring-boot.run.profiles=postgres`，该 Profile 自动开启 pgvector 读写（`rag.pgvector.enabled=true`）与 RLS 连接注入（`rag.rls.enabled=true`）；
4. 若更换 Embedding 模型维度 ≠ 768，需同步修改 `vector_store` 表的 `vector(768)` 与 `rag.embedding.dimensions`。

**验证租户隔离（RLS 兜底生效）**：用两个团队的账号交叉检索对方文档，应互不可见；即使应用层过滤被意外绕过，RLS 也会在数据库层拦截。

---

## 10. 切换云端 AI 模型

默认配置完全离线可运行（hashing Embedding + extractive 生成），接入真实模型后回答质量显著提升：

```yaml
rag:
  embedding:
    provider: openai
    dimensions: 1536              # 与所选模型一致，并同步调整 pgvector 维度
    openai:
      base-url: https://api.openai.com   # 或任意 OpenAI 兼容网关
      model: text-embedding-3-small
      api-key: ${OPENAI_API_KEY}
  llm:
    provider: openai
    openai:
      base-url: https://api.openai.com
      model: gpt-4o-mini
      api-key: ${LLM_API_KEY}
      timeout-seconds: 60
```

切换后的必做动作：

1. **重建向量**：对所有文档执行"重新入库"（Embedding 模型变了，旧向量全部失效）；
2. **重新校准拒答阈值**：不同模型的余弦分布不同，用评估集重跑并调整 `min-relevance`；
3. **回归评估**：对比切换前后的 Recall@5。

> 自定义模型接入点：实现 `EmbeddingProvider` / `LlmProvider` 接口（见 `rag/embedding/`、`rag/llm/` 包）并在配置中选择，流水线零改动。

---

## 11. 常见问题 FAQ

**Q1：上传后一直是"待处理/处理中"？**
Worker 每 3 秒扫描一次，大文档处理需要时间；超过 1 分钟无变化时查看后端日志是否有异常。Docker 部署确认 backend 容器与数据库连接正常。

**Q2：文档状态"失败"，重新入库也不行？**
悬停查看失败原因。常见：加密 PDF、扫描件（无文字层，OCR 为 P1 能力）、损坏的 DOCX。可先转存为 TXT/MD 再上传。

**Q3：回答总说"资料中未找到"？**
依次确认：① 文档状态是否"已入库"；② 答案是否真的在文档里；③ 检索日志里该查询命中了什么；④ `min-relevance` 是否过高（误拒答则调低）。

**Q4：引用标了编号但点开文档找不到对应内容？**
引用定位到"文档 + 页码"。若内容对不上，点"引用错误"反馈，并检查 LLM 是否为 extractive 兜底模式（该模式直接摘录原文，一般不会错；openai 模式可能需要更强模型）。

**Q5：忘记管理员密码？**
由其他管理员调用 `PUT /api/v1/users/{id}`（带 `password` 字段）重置。若只有这一个管理员：开发环境删除 `backend/data/example_db.mv.db` 后重启（会重建默认账户，**本地数据全部清空**）；生产环境由运维直连数据库更新 `users.password`（需 BCrypt 格式）。

**Q6：换电脑/重新部署后文档"已入库"但搜不到？**
向量存在数据库中，原始文件在 `rag.storage.base-dir` 指向的目录——迁移时两者要一起迁移；只迁了数据库没迁文件时，"重新入库"会因读不到文件而失败。

**Q7：多人同时用会有问题吗？**
MVP 设计目标 20 并发问答、单机部署。入库是异步的不阻塞上传；检索 P95 目标 ≤1.5s。

**Q8：数据安全怎么保障？**
传输：生产强制 HTTPS（`jwt.cookie-secure=true`）；存储：密码 BCrypt、RefreshToken HttpOnly Cookie（JS 不可读）；隔离：应用层强制租户过滤 + 数据库 RLS 兜底；权限：20 项细粒度权限逐操作校验，普通用户调用写接口 403。

---

## 12. 数据存储与运维

| 数据 | 位置（开发模式） | 位置（Compose） |
|------|------------------|-----------------|
| 业务数据 + 向量（h2 Profile） | `backend/data/example_db.mv.db` | — |
| 业务数据 + 向量（postgres Profile） | PostgreSQL 实例 | `pgdata` 卷 |
| 原始文件 | `backend/data/files/` | `files` 卷（容器内 `/app/data/files`） |
| 检索/审计日志 | 数据库表 `retrieval_logs` / `audit_logs` | 同左 |

**备份要点**：数据库与文件目录**必须一起备份**（向量与原文件互相依赖）；恢复后无需重建索引，直接可用。

**日志排查路径**：问答不准 → 检索日志（`/admin/logs/retrieval`）→ 调切片/检索参数 → 评估集回归。后端应用日志为标准输出（Compose 下 `docker compose logs backend`）。

**升级注意**：更换 Embedding 模型后必须全量"重新入库"；数据库结构由 JPA 自动迁移（`ddl-auto: update`），大版本升级前先备份。

---

## 附：验收自检清单（10 分钟）

- [ ] `GET /actuator/health` 返回 UP
- [ ] `admin@example.com` 登录成功，`user@example.com` 登录成功
- [ ] 普通成员调 `POST /api/v1/roles` 返回 403
- [ ] 上传一份 TXT/MD，状态变为"已入库"
- [ ] 问答命中文档内容且引用可定位；无关问题返回"资料中未找到"
- [ ] 评估跑通：Recall@5 ≥ 0.8、拒答准确率 ≥ 0.9（正式验收用 30–50 题）
- [ ] 删除文档后立即检索不到
- [ ] 用两个团队账号交叉检索，互不可见
