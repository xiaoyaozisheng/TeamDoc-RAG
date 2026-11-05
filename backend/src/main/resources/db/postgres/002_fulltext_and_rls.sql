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
-- ---------------------------------------------------------------------------

ALTER TABLE documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE chunks ENABLE ROW LEVEL SECURITY;
ALTER TABLE conversations ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_documents ON documents
    USING (tenant_id = current_setting('app.current_tenant')::bigint);
CREATE POLICY tenant_isolation_chunks ON chunks
    USING (tenant_id = current_setting('app.current_tenant')::bigint);
CREATE POLICY tenant_isolation_conversations ON conversations
    USING (tenant_id = current_setting('app.current_tenant')::bigint);

-- vector_store 走 metadata，无法用 RLS（表达式策略限制），由检索 SQL 的
-- metadata->>'tenant_id' = ? 强制过滤 + 索引保证性能。

-- 验证方式（验收 11.1）：以租户 A 身份调用检索接口，确认返回不含租户 B 数据；
-- 在应用层故意移除 tenant_id 过滤后重测，RLS 应仍拦截跨租户行。
