-- ============================================================================
-- 全文索引与租户 RLS：应用首次成功启动（JPA 建表完成）后手动执行一次
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 2. 混合检索的全文索引（rag.keyword-engine=pg 时使用）
-- ---------------------------------------------------------------------------

CREATE INDEX IF NOT EXISTS chunks_fts_idx ON chunks
    USING GIN (to_tsvector('simple', content));

-- ---------------------------------------------------------------------------
-- 3. 租户隔离 RLS（TEN-04）：应用层显式过滤是第一道防线，RLS 是数据库级兜底。
--    会话变量 app.current_tenant 由 TenantRlsDataSource 在每次取连接时注入。
--
--    关键点：必须用 current_setting(..., true)（missing_ok），否则任何未注入
--    租户上下文的线程（入库 Worker 定时扫描、SSE 异步线程、管理任务）执行查询
--    会直接报 "unrecognized configuration parameter" 而非被过滤。
--
--    语义：
--    - 已注入租户上下文  -> 仅可见本租户行（兜底拦截跨租户访问）；
--    - 未注入（NULL）    -> 视为系统级线程，放行全部行。
--      面向用户的业务查询始终显式携带 tenant_id 过滤（应用层第一道防线），
--      未注入上下文的只有系统后台线程，因此不会造成用户侧越权；
--      这是为了让异步 Worker / SSE / 管理端在无租户会话时仍能正常工作。
-- ---------------------------------------------------------------------------

ALTER TABLE documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE chunks ENABLE ROW LEVEL SECURITY;
ALTER TABLE conversations ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation_documents ON documents;
DROP POLICY IF EXISTS tenant_isolation_chunks ON chunks;
DROP POLICY IF EXISTS tenant_isolation_conversations ON conversations;

CREATE POLICY tenant_isolation_documents ON documents
    USING (tenant_id = COALESCE(current_setting('app.current_tenant', true)::bigint, tenant_id));
CREATE POLICY tenant_isolation_chunks ON chunks
    USING (tenant_id = COALESCE(current_setting('app.current_tenant', true)::bigint, tenant_id));
CREATE POLICY tenant_isolation_conversations ON conversations
    USING (tenant_id = COALESCE(current_setting('app.current_tenant', true)::bigint, tenant_id));

-- vector_store 走 metadata，无法用 RLS（表达式策略限制），由检索 SQL 的
-- metadata->>'tenant_id' = ? 强制过滤 + 索引保证性能。

-- 验证方式（验收 11.1）：以租户 A 身份调用检索接口，确认返回不含租户 B 数据；
-- 在应用层故意移除 tenant_id 过滤后重测，RLS 应仍拦截跨租户行；
-- 以 SET app.current_tenant = '' 模拟 Worker 线程，确认扫描仍可返回（不报错）。
