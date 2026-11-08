# 贡献指南

感谢关注 TeamDoc RAG！欢迎通过 Issue 反馈问题、提交 PR 贡献代码。

## 开发环境搭建

```bash
# 后端（JDK 17 + Maven，默认 h2 Profile 开箱即用）
cd backend && mvn spring-boot:run

# 前端（Node 18+）
cd frontend && npm install && npm run dev
```

详见 [使用手册](TeamDoc-RAG-使用手册.md) 第 1 节。

## 开发流程

1. 从 `main` 拉出功能分支：`git checkout -b feat/your-feature`
2. 开发并自测：
   - 后端：`mvn test`（35 个测试，含全链路集成测试，必须全绿）
   - 前端：`npx tsc --noEmit && npm run build`
3. 提交 PR 到 `main`，CI 通过后合并

## 提交规范

遵循 [Conventional Commits](https://www.conventionalcommits.org/zh-hans/)：

```
feat(rag): 支持混合检索权重配置
fix(auth): 修复刷新令牌轮换竞态
docs: 更新使用手册部署章节
```

类型：`feat` / `fix` / `docs` / `refactor` / `test` / `chore` / `perf`

## 代码规范

- 后端：Controller 使用 `@PreAuthorize` 逐操作声明权限；业务数据一律从认证上下文取 `tenant_id`，禁止信任客户端传入；新增功能须附带测试
- 前端：TypeScript 严格模式；请求逻辑集中在 `services/`
- 安全相关改动（认证/令牌/权限）请额外在 PR 描述中说明威胁模型考量

## 报告安全问题

请勿在公开 Issue 中提交安全漏洞，见 [SECURITY.md](SECURITY.md)。
