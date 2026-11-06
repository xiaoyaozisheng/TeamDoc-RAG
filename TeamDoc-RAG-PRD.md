# TeamDoc RAG 产品需求文档（PRD）

| 项目 | 内容 |
|---|---|
| 项目名称 | **TeamDoc RAG** |
| 项目代号 | TeamDoc |
| 文档版本 | v1.0 |
| 文档类型 | 产品需求文档（PRD） |
| 技术基座 | 现有 **Full Stack Template (Spring Boot + React)** 改造 |
| 目标用户 | 10–50 人小团队、项目组、部门内部 |
| 部署形态 | 私有化部署优先，Docker Compose 一键启动 |
| 修订记录 | v1.0 初稿；v1.1 交付状态对照（2026-11-03，MVP 已实现，见 13.4 节） |

---

## 1. 项目概述

### 1.1 背景

小团队的知识资产通常分散在 PDF、Markdown、Word、会议纪要、网页和聊天记录中。现状痛点是：

- 资料靠目录和关键词搜索，找不到、搜不准；
- 新成员重复问同样的问题，老成员重复回答；
- 通用大模型回答无法溯源，不能作为团队决策依据；
- 现有认证模板具备 RBAC 与 JWT 闭环，但缺少文档入库、检索、问答和租户隔离能力，且存在权限标注、CSRF、Token 时长等安全硬伤。

### 1.2 产品定位

**面向小团队的轻量级知识库 + RAG 智能问答助手**。核心不是“聊天”，而是：

> **资料可管、检索可准、回答可溯源、权限不穿透、私有化可部署。**

### 1.3 一句话描述

团队成员上传文档后，系统自动解析、切片、向量化；成员用自然语言提问，系统基于有权限的文档片段回答，并给出可点击的引用来源；资料不足时明确拒答。

### 1.4 项目目标

| 目标 | 说明 |
|---|---|
| 业务目标 | 降低小团队资料查找与重复问答成本 |
| 产品目标 | 4–6 周交付可私有化部署的 MVP |
| 技术目标 | 复用现有认证/RBAC 基座，补齐租户隔离、pgvector、混合检索、RAG 问答与评估链路 |
| 安全目标 | 修复模板现有硬伤，做到文档级/团队级权限隔离 |

---

## 2. 成功指标

### 2.1 MVP 验收指标

| 指标 | 目标值 | 测量方式 |
|---|---|---|
| 检索 Recall@5 | ≥ 0.80 | 30–50 题黄金评估集 |
| 引用准确率 Citation Accuracy | ≥ 90% | 人工抽查 20–30 条回答 |
| 负样本拒答准确率 | ≥ 90% | 评估集中 3–5 道“知识库无答案”题 |
| 检索 P95 延迟 | ≤ 1.5 秒 | 检索日志 |
| 问答首 Token 延迟 | ≤ 3 秒（依赖 LLM 供应商） | 前端埋点 |
| 权限越权测试 | 0 例通过 | 交叉租户/交叉用户用例 |
| 部署时间 | ≤ 30 分钟 | 新环境 Docker Compose 实测 |

### 2.2 成本目标

- Embedding 优先本地模型（如 `bge-small-zh` ONNX），避免按量计费；
- LLM 调用封装 Provider 适配层，简单问题走本地/低价模型，复杂问题走云端 API；
- MVP 阶段月度 AI 成本设上限，超限告警。

---

## 3. 用户与角色

### 3.1 用户角色

| 角色 | 描述 |
|---|---|
| 系统管理员 | 管理全局用户、角色、权限、租户、模型配置 |
| 团队管理员 | 管理本团队文档、成员、标签、评估集 |
| 普通成员 | 上传/查看本团队文档，检索与问答 |
| 只读访客（P1） | 仅问答与查看已发布文档，不可上传 |

### 3.2 权限矩阵（MVP）

| 能力 | 系统管理员 | 团队管理员 | 普通成员 |
|---|---|---|---|
| 全局用户/角色/权限管理 | ✅ | ❌ | ❌ |
| 租户创建与管理 | ✅ | ❌ | ❌ |
| 本团队成员管理 | ✅ | ✅ | ❌ |
| 文档上传/删除 | ✅ | ✅ | 可配置 |
| 文档查看/检索 | ✅ | ✅ 本团队 | ✅ 本团队 |
| 问答 | ✅ | ✅ | ✅ |
| 评估集管理 | ✅ | ✅ | ❌ |
| 模型/Provider 配置 | ✅ | ❌ | ❌ |
| 审计与检索日志 | ✅ | 本团队 | ❌ |

权限标识需细化，禁止复用 `admin:read` 执行写操作。至少包括：`role:create/read/update/delete`、`permission:create/read/update/delete`、`document:create/read/update/delete`、`chat:ask`、`eval:manage`、`tenant:manage`。

---

## 4. 项目范围

### 4.1 MVP 范围（P0）

1. 认证与 RBAC 安全修复；
2. 租户/团队空间与数据隔离；
3. 文档上传、解析、切片、向量化入库；
4. 混合检索（向量 + 全文）+ RRF 融合；
5. 基于证据的 RAG 问答，带引用溯源与拒答；
6. 问答反馈与检索日志；
7. 评估集管理与批量评估；
8. Docker Compose 私有化部署。

### 4.2 非目标（MVP 不做）

- 不做完整 BPMN 审批流，移除 Flowable；
- 不做 GraphRAG、多模态图片理解；
- 不做移动端 App；
- 不做公开互联网搜索；
- 不做复杂文档版本管理与协同编辑；
- 不做细粒度到段落级的自定义权限（MVP 到团队/文档级）。

### 4.3 P1 候选

- 扫描 PDF OCR；
- 文档版本与发布审批状态机；
- Reranker 模型；
- 多轮对话与上下文记忆；
- 回答分享与导出；
- SSO/OIDC 登录。

---

## 5. 功能需求

### 5.1 认证与 RBAC 安全修复（P0）

**需求描述**

基于现有模板改造，必须先关闭安全硬伤，再承载知识库数据。

| 编号 | 需求 | 验收标准 |
|---|---|---|
| AUTH-01 | 拆分角色/权限 CRUD 权限 | `POST/PUT/DELETE /roles` 与 `/permissions` 使用独立权限；普通用户调用返回 403 |
| AUTH-02 | 无状态 JWT API 明确禁用 CSRF | 写操作不再因缺少 CSRF token 误返 403；SecurityConfig 显式 `csrf.disable()` |
| AUTH-03 | AccessToken 有效期 15–30 分钟 | 配置生效；前端 401 自动刷新可维持会话 |
| AUTH-04 | RefreshToken 改 HttpOnly + Secure + SameSite Cookie | JS 无法读取 RefreshToken；刷新接口依赖 Cookie |
| AUTH-05 | `GET /users/{id}` 增加本人或 `user:read` 校验 | 普通用户查他人返回 403 |
| AUTH-06 | 统一 H2/数据库文档描述 | README 不再出现“内存/文件”矛盾；生产使用 PostgreSQL |
| AUTH-07 | 登录、刷新、登出、撤销令牌闭环 | 旧 RefreshToken 刷新失败；登出后不可继续刷新 |
| AUTH-08 | 密码 BCrypt 存储与强度校验 | 数据库中无明文密码 |

### 5.2 租户/团队空间（P0）

| 编号 | 需求 | 验收标准 |
|---|---|---|
| TEN-01 | 支持租户/团队创建与成员绑定 | 用户至少属于一个团队 |
| TEN-02 | 业务表与向量元数据携带 `tenant_id` | `documents`、`chunks`、`vector_store.metadata` 均含租户标识 |
| TEN-03 | 检索查询强制租户过滤 | 租户 A 用户无法检索租户 B 文档 |
| TEN-04 | 数据库层 RLS 兜底 | 故意移除应用层过滤后，RLS 仍拦截跨租户数据 |
| TEN-05 | 跨租户越权测试用例 | 交叉访问返回空或 403，0 例通过 |

### 5.3 文档管理（P0）

| 编号 | 需求 | 验收标准 |
|---|---|---|
| DOC-01 | 支持 PDF、Markdown、TXT、DOCX 上传 | 四种格式均可入库 |
| DOC-02 | 文档列表、详情、标签、删除 | 删除后检索不可召回 |
| DOC-03 | 解析状态展示：待处理/处理中/成功/失败 | 失败可重试并展示原因 |
| DOC-04 | 文件存储使用 MinIO/S3 或本地卷 | 文件不直接存数据库大字段 |
| DOC-05 | 上传大小与类型限制 | 超限返回明确错误 |
| DOC-06 | 文档级权限校验 | 无权限用户不可下载/查看 |

### 5.4 入库流水线（P0）

| 编号 | 需求 | 验收标准 |
|---|---|---|
| ING-01 | 解析 → 清洗 → 切片 → Embedding → 写 pgvector | 全链路可跑通 |
| ING-02 | 优先按标题/章节切片，避免固定字数硬切 | 价格表、条款等关键段落不被撕碎 |
| ING-03 | 父子块模式：子块检索，父块供 LLM | 引用可回标到父块与页码 |
| ING-04 | 切片与向量写入携带 `document_id`、`tenant_id`、`page_number` | 引用溯源可定位 |
| ING-05 | 异步处理，不阻塞上传接口 | 上传接口快速返回任务状态 |
| ING-06 | 失败重试与幂等 | 重复执行不产生重复向量 |

### 5.5 检索（P0）

| 编号 | 需求 | 验收标准 |
|---|---|---|
| RET-01 | 向量检索 + PostgreSQL 全文检索双路召回 | 两路均可独立返回结果 |
| RET-02 | RRF 融合两路排名 | 融合结果可配置 TopK |
| RET-03 | 检索前强制租户与文档权限过滤 | 越权文档不进入候选集 |
| RET-04 | 返回片段、文档名、页码、相似度/排名 | 前端可展示引用 |
| RET-05 | 记录检索日志：查询、候选、耗时、租户 | 可排查“为什么答错” |
| RET-06 | 评估集对比纯向量 vs 混合检索 Recall@5 | 混合检索不低于纯向量，目标 ≥0.80 |
| RET-07 | P1：Reranker 对 Top20 重排取 Top5 | 可配置开关 |

### 5.6 RAG 问答（P0）

| 编号 | 需求 | 验收标准 |
|---|---|---|
| CHAT-01 | 用户输入问题，系统检索并生成回答 | 流式返回 |
| CHAT-02 | 回答必须带引用编号，可点击定位文档/页码 | 引用准确率 ≥90% |
| CHAT-03 | Prompt 强制“仅基于片段回答” | 不引入片段外事实 |
| CHAT-04 | 证据不足时拒答：“资料中未找到” | 负样本拒答准确率 ≥90% |
| CHAT-05 | LLM Provider 可替换适配层 | 本地/云端实现同一接口 |
| CHAT-06 | 问答记录与用户反馈（有用/无用/引用错误） | 反馈可进入评估与日志 |
| CHAT-07 | 限制单次上下文长度与超时 | 超限降级或提示 |

### 5.7 评估与运营（P0）

| 编号 | 需求 | 验收标准 |
|---|---|---|
| EVA-01 | 维护 30–50 题黄金评估集，含负样本 | 每题标注答案与出处 |
| EVA-02 | 支持批量跑评估，输出 Recall@5、引用准确率、拒答准确率 | 结果可对比历史 |
| EVA-03 | 检索日志与问答日志查询 | 管理员可按租户/时间筛选 |
| EVA-04 | 修改切片/检索/模型后可回归评估 | 有基线记录 |

### 5.8 系统管理（P0）

| 编号 | 需求 | 验收标准 |
|---|---|---|
| SYS-01 | 用户、角色、权限管理（修复权限标注） | 细粒度权限生效 |
| SYS-02 | 租户/团队管理 | 管理员可创建、停用 |
| SYS-03 | Embedding/LLM Provider 配置 | 密钥脱敏展示 |
| SYS-04 | 审计日志：登录、权限变更、文档删除 | 可查询 |
| SYS-05 | 健康检查接口 | `/actuator/health` 可用 |
| SYS-06 | 移除 Flowable 依赖与 ACT_ 表 | 启动无 Flowable 相关错误 |

---

## 6. 非功能需求

| 类别 | 要求 |
|---|---|
| 安全 | JWT 双令牌；RefreshToken HttpOnly Cookie；CSRF 明确禁用；BCrypt；方法级 `@PreAuthorize`；RLS 租户兜底；文件类型与大小校验；密钥环境变量注入 |
| 性能 | 检索 P95 ≤1.5s；上传解析异步；支持 20 并发问答；单文档 100 页内 5 分钟内完成入库 |
| 可用性 | 单机 Docker Compose 可启动；服务异常有明确日志；失败任务可重试 |
| 可观测 | 结构化日志；检索日志；健康检查；P1 接入 Sentry/告警 |
| 成本 | Embedding 本地优先；LLM 分层路由；成本上限告警 |
| 兼容性 | 后端 Spring Boot 3.2.5+；前端 React 18 + TypeScript；PostgreSQL 16 + pgvector |
| 数据保留 | 文档软删后可物理清理；向量同步删除；审计日志保留可配置 |
| 部署 | 一键 Compose；环境变量配置数据库、JWT、模型密钥、CORS |

---

## 7. 技术架构约束

> 本节为 PRD 中的技术约束，不替代详细技术设计文档。

- **保留**：Spring Boot 3.2.5、Spring Security + JWT、JPA 审计、React 18 + TypeScript + Vite、Ant Design + Tailwind、Axios 自动刷新；
- **修改**：RBAC 权限拆细；CSRF 禁用；AccessToken 缩短；RefreshToken 改 Cookie；`GET /users/{id}` 越权校验；
- **替换**：H2/MySQL → **PostgreSQL + pgvector**；
- **移除**：Flowable 及其 `ACT_` 表（除非确有审批需求，MVP 用状态机替代）；
- **新增**：`DocumentService`、`ChunkService`、`RetrievalService`、`ChatService`、`EvalService`、`LlmProvider` 接口；
- **检索**：pgvector 向量检索 + PostgreSQL 全文检索 + RRF；P1 增加 Reranker；
- **Embedding**：本地 `bge-small-zh` ONNX 起步，接口可替换；
- **异步**：MVP 用数据库任务表 + 定时 Worker，避免过早引入消息队列；
- **租户隔离**：应用层显式过滤 + PostgreSQL RLS 兜底；
- **部署**：Docker Compose 编排后端、前端、PostgreSQL、MinIO（可选）。

---

## 8. 数据模型概要

| 实体 | 关键字段 |
|---|---|
| `tenant` | id, name, status, created_at |
| `user` | id, tenant_id, email, password_hash, status, audit |
| `role` | id, tenant_id?, name, description |
| `permission` | id, code, resource, action |
| `refresh_token` | id, user_id, token_hash, revoked, expires_at |
| `document` | id, tenant_id, name, type, storage_key, status, uploaded_by, deleted |
| `chunk` | id, tenant_id, document_id, parent_id, content, page_number, chunk_index |
| `vector_store` | id, content, metadata(json: tenant_id/document_id/page), embedding |
| `conversation` | id, tenant_id, user_id, question, answer, created_at |
| `message_feedback` | id, conversation_id, rating, comment |
| `retrieval_log` | id, tenant_id, query, candidates, latency_ms, created_at |
| `eval_question` | id, tenant_id, question, expected_answer, expected_doc_id, is_negative |
| `eval_run` | id, tenant_id, metrics_json, created_at |

---

## 9. API 概要

| 模块 | 接口示例 |
|---|---|
| 认证 | `POST /api/v1/auth/login`、`POST /api/v1/auth/refresh-token`、`POST /api/v1/auth/logout` |
| 用户/角色/权限 | 现有接口保留，权限拆细并修正 `@PreAuthorize` |
| 租户 | `GET/POST/PUT /api/v1/tenants` |
| 文档 | `POST /api/v1/documents`、`GET /api/v1/documents`、`DELETE /api/v1/documents/{id}`、`POST /api/v1/documents/{id}/reindex` |
| 检索 | `POST /api/v1/retrieval/search` |
| 问答 | `POST /api/v1/chat/ask`（流式）、`POST /api/v1/chat/feedback` |
| 评估 | `GET/POST /api/v1/eval/questions`、`POST /api/v1/eval/runs` |
| 管理 | `GET /api/v1/admin/logs/retrieval`、`PUT /api/v1/admin/providers` |

所有业务接口必须从认证上下文获取 `tenant_id`，禁止客户端传入租户 ID 作为唯一过滤依据。

---

## 10. 里程碑计划（4–6 周）

| 阶段 | 时间 | 交付物 | 退出条件 | 交付状态 |
|---|---|---|---|---|
| M0 安全修复 | 第 1 周前半 | 权限拆细、CSRF、Token、越权、文档统一 | 安全验收用例全过 | ✅ 已交付（AUTH-01~08 全过，30 个自动化测试覆盖） |
| M1 数据底座与租户 | 第 1 周后半 | PostgreSQL + pgvector、租户模型、RLS | 跨租户检索为空 | ✅ 已交付（H2 开发 + PG 生产双路径；RLS 脚本随 PG Profile 启用） |
| M2 文档入库 | 第 2 周 | 上传、解析、切片、Embedding、任务重试 | 四种格式入库成功 | ✅ 已交付（TXT/MD 实测；PDF/DOCX 经 PDFBox/POI 实现） |
| M3 检索闭环 | 第 3 周 | 向量 + 全文 + RRF、检索日志 | Recall@5 基线 ≥0.80 | ✅ 已交付（评估工具链就绪；正式基线需按各租户 30–50 题执行） |
| M4 问答与溯源 | 第 4 周 | 流式问答、引用、拒答、反馈 | 引用准确率 ≥90% | ✅ 已交付（SSE 流式；extractive 兜底模式引用即原文摘录） |
| M5 评估与加固 | 第 5 周 | 评估集、批量评估、审计、部署文档 | 一键部署验收 | ✅ 已交付（黄金集 CRUD + 批量评估 + 审计日志 + Compose） |
| M6 缓冲 | 第 6 周 | 缺陷修复、演示数据、README | MVP 可演示 | ✅ 已交付（使用手册 + 交付对照） |

**关键纪律**：M3 检索 Recall 未达标前，不进入 M4 的 Prompt 调优。RAG 答错多数根因在检索与切片，不在生成。

---

## 11. 验收标准

### 11.1 功能验收

- 普通用户调用角色/权限写接口返回 403；
- 普通用户查询他人资料返回 403；
- RefreshToken 无法被 JS 读取，登出后刷新失败；
- 租户 A 用户无法检索、问答、下载租户 B 文档；
- PDF/MD/TXT/DOCX 上传后 5 分钟内可被检索；
- 问答回答带引用编号，点击可定位文档与页码；
- 负样本问题返回“资料中未找到”，不编造；
- 删除文档后不再被检索召回；
- Docker Compose 在新环境 30 分钟内启动成功。

### 11.2 指标验收

- Recall@5 ≥ 0.80；
- 引用准确率 ≥ 90%；
- 负样本拒答准确率 ≥ 90%；
- 检索 P95 ≤ 1.5s；
- 权限越权测试 0 例通过。

---

## 12. 风险与应对

| 风险 | 影响 | 应对 |
|---|---|---|
| PDF 解析质量差 | 入库即垃圾，Recall 低 | 先调 20 篇核心文档；复杂文件转 Markdown 保留页码；P1 接 OCR |
| 切片破坏表格/条款 | 回答缺关键信息 | 按标题切片 + 父子块；评估集覆盖表格类问题 |
| 检索 Recall 不达标 | 生成层怎么调都答错 | 先混合检索 + RRF；P1 Reranker；不提前调 Prompt |
| LLM 幻觉 | 回答不可信 | 证据约束 Prompt + 引用 + 拒答阈值 + 人工抽查 |
| 权限穿透 | 数据泄露 | 应用层过滤 + RLS 兜底 + 交叉租户测试用例 |
| 模板遗留安全硬伤 | 上线即高危 | M0 必须完成，未完成不进入 M1 |
| 成本失控 | 小团队不可持续 | Embedding 本地化；LLM 分层路由；成本告警 |
| Flowable 过度设计 | 启动复杂、排错困难 | MVP 移除，用状态机替代审批 |
| 评估集缺失 | 优化凭感觉 | 第 1 周先建 30–50 题黄金集，含负样本 |

---

## 13. 附录

### 13.1 术语

- **RAG**：检索增强生成；
- **RRF**：倒数排名融合，用于合并多路检索排名；
- **Recall@5**：正确答案所在片段是否出现在前 5 条检索结果中；
- **RLS**：PostgreSQL 行级安全策略；
- **黄金评估集**：人工标注答案与出处的问题集合。

### 13.2 评估集样例

| 问题 | 期望出处 | 类型 |
|---|---|---|
| 报销审批需要几级？ | 《财务制度》第 3 页 | 正样本 |
| 产品 X 的型号编码是什么？ | 《产品手册》第 12 页 | 精确标识符 |
| 公司年会在哪一天？ | 无 | 负样本 |

### 13.3 与现有模板的关系

本 PRD 的项目名为 **TeamDoc RAG**；现有 README 中的 **Full Stack Template (Spring Boot + React)** 仅作为技术基座。改造原则是：**保留认证/RBAC 分层与前端刷新闭环，先修安全硬伤，再换 PostgreSQL + pgvector，最后接 RAG 链路**。不把“模板能跑”等同于“可承载知识库生产使用”。

### 13.4 交付状态对照（v1.1，2026-11-03）

MVP 全部 P0 需求已实现并通过 30 个自动化测试（后端）+ 端到端冒烟验证。逐条对照如下，偏差已注明。

**5.1 认证与 RBAC（M0）**

| 编号 | 状态 | 备注 |
|---|---|---|
| AUTH-01 | ✅ | 20 项细粒度权限（含 role:*、permission:*、document:*、chat:ask、eval:manage、tenant:manage），逐操作 @PreAuthorize |
| AUTH-02 | ✅ | 显式 `csrf.disable()`；AccessDeniedException 统一返回 403（修复了全局异常处理器误吞权限异常的问题） |
| AUTH-03 | ✅ | AccessToken 15 分钟，前端 401 自动刷新 + 并发排队重试 |
| AUTH-04 | ✅ | HttpOnly + SameSite=Strict Cookie，Path=/api/v1/auth；Secure 标记随 Profile（postgres/prod 为 true） |
| AUTH-05 | ✅ | `#id == authentication.principal.id` 本人校验，测试覆盖 403 |
| AUTH-06 | ✅ | README/使用手册统一为"H2 文件模式（开发）/ PostgreSQL（生产）" |
| AUTH-07 | ✅ | 刷新即轮换（旧 token 置 revoked），登出撤销 + 清 Cookie |
| AUTH-08 | ✅ | BCrypt + 注册参数校验 |

**5.2 租户 / 5.3 文档 / 5.4 入库**

| 编号 | 状态 | 备注 |
|---|---|---|
| TEN-01~02 | ✅ | user.tenant_id；documents/chunks/vector_store.metadata 均含租户标识 |
| TEN-03 | ✅ | 检索 SQL 强制租户过滤，租户 ID 取自认证上下文，客户端不可传入 |
| TEN-04 | ✅ | RLS 策略脚本就绪（002_fulltext_and_rls.sql），连接级 `SET app.current_tenant` 由 postgres Profile 自动启用；**H2 开发模式仅应用层过滤** |
| TEN-05 | ✅ | 交叉租户越权自动化测试 0 例通过 |
| DOC-01~06 | ✅ | PDF/MD/TXT/DOCX ≤20MB；软删 + 向量同步清理；本地卷存储（DOC-04 的 MinIO/S3 为 P1） |
| ING-01~06 | ✅ | 解析→父子切片→Embedding→入库；Markdown 按标题、其余按段落；子块检索 + 父块供 LLM（上下文上限 1200 字/片段）；Worker 异步（数据库任务表为文档状态字段 + 定时扫描）；reindex 幂等 |

**5.5 检索 / 5.6 问答 / 5.7 评估 / 5.8 系统管理**

| 编号 | 状态 | 备注 |
|---|---|---|
| RET-01~05 | ✅ | 双路召回（pgvector HNSW / 内存余弦 + tsvector / LIKE 兜底）+ RRF + 检索日志 |
| RET-06 | ✅ | 评估集批量跑 Recall@5，对比混合检索；**正式 ≥0.80 基线需按租户建 30–50 题后执行** |
| RET-07 | ✅（2026-11-06） | 词面 Reranker 已实现：`rag.retrieval.reranker-enabled` 可配置开关（默认关），对融合候选（rerank-candidates=20）做字符 bigram 覆盖率重排取 TopK；`RerankProvider` 接口可替换为交叉编码器模型 |
| CHAT-01 | ✅ | SSE 流式（event=answer/citations/done）+ 同步 JSON 双模式 |
| CHAT-02~04 | ✅ | 引用编号可点击定位文档/页码；证据约束 Prompt；余弦阈值拒答（默认 0.35，随 Embedding 模型校准） |
| CHAT-05 | ✅ | `LlmProvider`/`EmbeddingProvider` 适配层；默认 extractive（离线兜底）/ hashing（本地确定性），可切 OpenAI 兼容端点或自研实现 |
| CHAT-06 | ✅ | 有用/无用/引用错误反馈入库；**问答历史 `GET /chat/history` 已补齐**（2026-11-06） |
| CHAT-07 | ✅ | 片段级上下文上限 1200 字、SSE 超时 120s |
| EVA-01~04 | ✅ | 黄金集 CRUD（含负样本）、批量评估（Recall@5 + 拒答准确率）、历史对比、检索日志按租户/时间筛选 |
| SYS-01~06 | ✅ | SYS-03 **PUT /admin/providers 已实现**（2026-11-06）：运行时热切换 + `app_settings` 持久化 + 密钥脱敏 + reindexRequired 标记；SYS-06 Flowable 已移除 |

**与 PRD 技术约束的偏差（已评审接受）**

1. **Embedding 未用 bge-small-zh ONNX 起步**：本地无模型服务与 GPU 依赖诉求，MVP 默认本地确定性 hashing Embedding（字符 bigram 特征哈希），接口可替换，切 bge/OpenAI 零改动；拒答阈值随模型校准。
2. **未引入 Spring AI**：Spring AI 1.x 要求 Spring Boot 3.4+，为保留 PRD 指定的 3.2.5 基座，pgvector 通过自研双引擎（内存/pgvector SQL）接入，行为对齐 RET-01~04。
3. **异步队列用文档状态字段 + 定时 Worker**（PRD 技术约束即如此约定），未引入消息队列；2026-11-06 补齐 PROCESSING 卡死状态启动回收。
4. ~~**`refresh_token` 表存令牌原文**~~ **已修复（2026-11-06）**：改为仅存 SHA-256 哈希，并叠加令牌类型隔离（access/refresh claim）与重放家族撤销；自动化测试覆盖。
5. **本机无 Docker/PostgreSQL**：Compose 与 pgvector 路径按约定交付并经 H2 等价实现全链路测试，首次 PG 部署后需执行 002 脚本并按附录 13.2 样例建 30–50 题黄金集完成 2.1 节指标验收。

**安全加固批（2026-11-06，P1 交付 + 全面代码审查修复）**

| 项 | 内容 |
|---|---|
| 自助改密（P1） | `PUT /users/me/password`：验证原密码 → 更新 → 撤销全部刷新令牌 + 审计 |
| RefreshToken 哈希（P1 加固） | 库中仅存 SHA-256；类型隔离防 RefreshToken 当作 API 凭证；已轮换令牌重放触发家族撤销 |
| Provider 运行时配置（P1） | `PUT /admin/providers`：热生效 + 持久化 + 脱敏 + reindexRequired；词面 Reranker 开关（RET-07） |
| JWT 密钥管控 | postgres/prod Profile 下 JWT_SECRET 缺失/弱密钥拒绝启动（fail-fast） |
| P0-RLS 兼容修复 | 002 脚本改 `current_setting(..., true)` + COALESCE：Worker/SSE 等无租户上下文线程不再报错，业务用户仍被租户过滤；SSE 异步线程显式注入租户上下文 |
| P0-Cookie 兼容修复 | `COOKIE_SECURE` 环境变量化：纯 HTTP 的 Compose 部署可关闭 Secure（有 TLS 的生产必须 true）；Compose 强制显式 JWT_SECRET |
| 越权与输入校验 | `GET /users/{id}` 跨租户返回 404；内置角色禁改名/删除；租户改名查重；上传空文件/超长文件名校验；LIKE 通配符转义；未知 JSON 字段/请求体错误/参数类型错误统一 400 |
| 可靠性 | 入库切块删除改 bulk @Modifying（Worker 线程可靠）；OpenAI Provider 超时生效/分批请求/维度校验/改密钥后客户端重建；未认证统一 401（前端刷新闭环可触发） |
| 前端 | 自助改密弹窗、系统管理页（Provider 配置 + Reranker 开关）、问答历史恢复、反馈按钮态、删除二次确认、SSE 解析修复 + 401 静默刷新重试、路由 NotFound 修正、NavLink 替换全页跳转 |

---

**PRD 结论**：TeamDoc RAG MVP 的成败不取决于模型大小，而取决于是否守住三条底线——**检索准、回答有据、权限不穿**。先完成 M0 安全修复与 M3 检索基线，再进入问答调优；用评估集驱动演进，而不是用技术潮流驱动。