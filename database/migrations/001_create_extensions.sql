-- Fase 2 — DDL PostgreSQL. Ver docs/data-model.md para la especificación completa.
-- Extensiones requeridas por el esquema:
--   pgcrypto -> gen_random_uuid() para las PK de tipo UUID (rw_users, rw_channels, rw_refresh_tokens)
--   vector   -> tipo `vector` de pgvector para almacenar embeddings de mensajes (RAG del copiloto)

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS vector;
