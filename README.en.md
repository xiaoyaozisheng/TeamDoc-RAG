# TeamDoc RAG (Spring Boot + React)

<div align="center">

![CI](https://github.com/xiaoyaozisheng/TeamDoc-RAG/actions/workflows/ci.yml/badge.svg)
![License](https://img.shields.io/badge/license-MIT-green.svg)
![Java](https://img.shields.io/badge/Java-17-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen.svg)
![React](https://img.shields.io/badge/React-18-61dafb.svg)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%20%2B%20pgvector-blue.svg)

English | [简体中文](README.md)

</div>

A **lightweight knowledge base + RAG assistant for small teams**: manageable documents, precise retrieval, traceable answers, tenant-isolated permissions, and self-hosted deployment.

Upload documents — the system parses, chunks, and embeds them automatically. Ask questions in natural language — the system answers strictly from the document chunks you are allowed to see, with clickable citations. When evidence is insufficient, it explicitly refuses to answer instead of hallucinating.

> The primary documentation is in Chinese. This file covers the essentials; see [README.md](README.md) and the [user manual](TeamDoc-RAG-使用手册.md) for full details.

## Highlights

- **Evidence-grounded answers** — every claim carries `[n]` citations (document + page); "not found in the materials" when evidence is insufficient
- **Hybrid retrieval** — pgvector HNSW (or in-memory cosine) + PostgreSQL full-text, fused with RRF; pluggable reranker
- **Tenant isolation done twice** — application-level tenant filtering everywhere (tenant comes from the JWT, never from the client) plus PostgreSQL Row-Level Security as a database-side backstop
- **Hardened auth** — dual-token JWT (15-min access token; refresh token in an HttpOnly cookie, stored only as a SHA-256 hash with rotation + replay family-revocation), 20 granular RBAC permissions with per-operation `@PreAuthorize`
- **Evaluation-driven** — golden question set (including negative samples), batch Recall@5 / rejection-accuracy runs, retrieval logs for debugging bad answers
- **Self-hosted** — one-command Docker Compose (PostgreSQL 16 + pgvector, backend, nginx frontend)

## Architecture

```
Upload → parse (PDF/MD/TXT/DOCX) → parent+child chunking → embedding → chunks (+ pgvector)
Ask    → hybrid retrieval (vector + full-text) → RRF fusion → relevance gate →
         evidence-constrained prompt → LLM (pluggable provider) → cited answer
```

Embedding and LLM are interface-based providers: a deterministic local hashing embedder and an extractive (offline) answerer run out of the box with zero external dependencies; switch to OpenAI-compatible endpoints or your own implementations via configuration.

## Quick Start

```bash
# Backend (JDK 17 + Maven, embedded H2 by default — zero setup)
cd backend && mvn spring-boot:run        # http://localhost:8080

# Frontend
cd frontend && npm install && npm run dev  # http://localhost:5173
```

Default accounts (change passwords immediately in any real deployment): `admin@example.com / admin123`, `user@example.com / user123`.

### Docker Compose (production-style)

```bash
export JWT_SECRET=<32+ random chars>
export DB_PASSWORD=<db password>
docker compose up -d --build
# then run db/postgres/002_fulltext_and_rls.sql once (see README.md)
```

## Testing

```bash
cd backend && mvn test    # 35 tests: end-to-end RAG flow, tenant isolation, permission matrix, RRF, reranker
cd frontend && npx tsc --noEmit && npm run build
```

## Docs

- [README.md](README.md) — full docs (Chinese)
- [User manual](TeamDoc-RAG-使用手册.md) — deployment, configuration reference, FAQ (Chinese)
- [PRD](TeamDoc-RAG-PRD.md) — requirements, acceptance criteria, delivery matrix (Chinese)
- [CONTRIBUTING.md](CONTRIBUTING.md) — dev setup, commit conventions, PR flow
- [SECURITY.md](SECURITY.md) — vulnerability reporting

## License

[MIT](LICENSE)
