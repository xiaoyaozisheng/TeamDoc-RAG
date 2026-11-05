-- ============================================================================
-- PostgreSQL + pgvector 初始化脚本（切到 postgres profile 后手动执行一次）
-- 业务表（users/roles/...）由 JPA ddl-auto=update 自动建，本脚本补齐：
--   1) 扩展与 vector_store 表 + HNSW 索引
--   2) chunks 全文检索 GIN 索引
--   3) 租户隔离 RLS 策略（TEN-04 数据库级兜底）
-- 注意：JPA 建表须先完成（应用至少成功启动一次），再执行第 2、3 部分。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. pgvector 基础设施
-- ---------------------------------------------------------------------------

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 维度 768 需与 rag.embedding.dimensions 一致（bge-small-zh 为 512，bge-base-zh 为 768）
CREATE TABLE IF NOT EXISTS vector_store (
    id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content text,
    metadata json,
    embedding vector(768)
);

-- HNSW：m=16、ef_construction=64 是小团队起步阶段的均衡值；
-- 文档量达到数万级且召回不足时，再对比 m=32 / ef_construction=128
CREATE INDEX IF NOT EXISTS vector_store_hnsw_idx ON vector_store USING hnsw
    (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

CREATE INDEX IF NOT EXISTS vector_store_doc_idx ON vector_store ((metadata->>'document_id'));
CREATE INDEX IF NOT EXISTS vector_store_tenant_idx ON vector_store ((metadata->>'tenant_id'));
