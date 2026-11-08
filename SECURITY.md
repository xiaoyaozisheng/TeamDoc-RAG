# 安全策略

## 报告漏洞

如果你发现安全漏洞，请**不要**在公开 Issue 中提交，而是使用 GitHub 的私密渠道：

- 点击仓库 Security 标签页中的 **"Report a vulnerability"**（GitHub Private Vulnerability Reporting），或
- 通过 [Security Advisories](https://github.com/xiaoyaozisheng/TeamDoc-RAG/security/advisories/new) 私密提交

我们会在 **72 小时内**确认收到，并在确认后尽快提供修复。

## 支持版本

| 版本 | 状态 |
|------|------|
| main 分支最新提交 | ✅ 接收安全修复 |
| 历史 tag / 旧提交 | ❌ 不维护，请升级 |

## 已知设计边界

以下为当前实现的明确设计取舍（详见 PRD 与代码注释），不属于漏洞，但欢迎通过上述渠道讨论改进：

- 默认账户（`admin@example.com` / `admin123`）由 `DataInitializer` 创建，供首次登录使用——**生产部署后必须立即修改**；
- 开发 Profile（h2）下 `JWT_SECRET` 缺失时使用固定开发密钥并告警；postgres/prod Profile 下拒绝启动；
- H2 控制台与 Swagger 仅在开发 Profile 开放（`security.dev-endpoints-enabled`）。

## 安全特性一览

- JWT 双令牌：AccessToken 15 分钟；RefreshToken 7 天、数据库仅存 SHA-256 哈希、HttpOnly Cookie 传输
- 重放保护：已轮换 RefreshToken 被重放即撤销该用户全部刷新令牌（家族保护）
- 令牌类型隔离：access / refresh 不可互用
- RBAC：20 项细粒度权限 + 逐操作 `@PreAuthorize`；跨租户查询返回 404（不泄露存在性）
- 租户隔离：应用层强制过滤 + PostgreSQL RLS 兜底
- 生产密钥 fail-fast：postgres/prod Profile 下密钥缺失或过弱拒绝启动
