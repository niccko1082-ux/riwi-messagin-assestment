-- Fase 2 — DDL PostgreSQL. FKs (con ON DELETE explícito y justificado en docs/data-model.md
-- §5), UNIQUE y CHECK. Ningún ON DELETE es el default implícito: usuarios y mensajes nunca se
-- borran físicamente (se desactivan / marcan is_deleted), así que la mayoría de FKs hacia
-- ellos son RESTRICT.

-- ── rw_users ────────────────────────────────────────────────────────────────
-- La unicidad de email se declara como índice único case-insensitive en 004
-- (ux_rw_users_email_lower), no como UNIQUE(email) plano: el CHECK de formato de abajo
-- ya es case-insensitive (~*), y un UNIQUE simple dejaría pasar 'Camilo@riwi.io' y
-- 'camilo@riwi.io' como cuentas distintas para el mismo correo real.

ALTER TABLE rw_users
    ADD CONSTRAINT ck_rw_users_email_format CHECK (email ~* '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$');

-- ── rw_channels ─────────────────────────────────────────────────────────────
ALTER TABLE rw_channels
    ADD CONSTRAINT fk_rw_channels_created_by FOREIGN KEY (created_by)
        REFERENCES rw_users (user_id) ON DELETE RESTRICT;

ALTER TABLE rw_channels
    ADD CONSTRAINT ck_rw_channels_type CHECK (channel_type IN ('direct', 'group'));

-- Regla de negocio: los canales directos (1:1) no tienen nombre propio; los grupales sí.
ALTER TABLE rw_channels
    ADD CONSTRAINT ck_rw_channels_name_by_type CHECK (
        (channel_type = 'group'  AND name IS NOT NULL AND length(btrim(name)) > 0)
     OR (channel_type = 'direct' AND name IS NULL)
    );

-- ── rw_channel_members ──────────────────────────────────────────────────────
ALTER TABLE rw_channel_members
    ADD CONSTRAINT fk_rw_channel_members_channel FOREIGN KEY (channel_id)
        REFERENCES rw_channels (channel_id) ON DELETE CASCADE;

ALTER TABLE rw_channel_members
    ADD CONSTRAINT fk_rw_channel_members_user FOREIGN KEY (user_id)
        REFERENCES rw_users (user_id) ON DELETE RESTRICT;

-- FK compuesta contra uq_rw_messages_channel_message: el motor garantiza que el "último
-- leído" de una membresía pertenece al MISMO canal de esa membresía. Se usa la sintaxis de
-- PostgreSQL 15+ "ON DELETE SET NULL (columna)", que anula únicamente last_read_message_id
-- en vez de las dos columnas de la FK — un SET NULL "clásico" (sin columna específica)
-- anularía también channel_id, que es parte de la PK de esta tabla, y violaría la PK.
ALTER TABLE rw_channel_members
    ADD CONSTRAINT fk_rw_channel_members_last_read FOREIGN KEY (channel_id, last_read_message_id)
        REFERENCES rw_messages (channel_id, message_id) ON DELETE SET NULL (last_read_message_id);

ALTER TABLE rw_channel_members
    ADD CONSTRAINT ck_rw_channel_members_role CHECK (role_in_channel IN ('owner', 'member'));

-- ── rw_messages ─────────────────────────────────────────────────────────────
ALTER TABLE rw_messages
    ADD CONSTRAINT fk_rw_messages_channel FOREIGN KEY (channel_id)
        REFERENCES rw_channels (channel_id) ON DELETE RESTRICT;

ALTER TABLE rw_messages
    ADD CONSTRAINT fk_rw_messages_sender FOREIGN KEY (sender_id)
        REFERENCES rw_users (user_id) ON DELETE RESTRICT;

ALTER TABLE rw_messages
    ADD CONSTRAINT ck_rw_messages_content_not_blank CHECK (length(btrim(content)) > 0);

ALTER TABLE rw_messages
    ADD CONSTRAINT ck_rw_messages_deleted_consistency CHECK (
        (is_deleted = FALSE AND deleted_at IS NULL)
     OR (is_deleted = TRUE  AND deleted_at IS NOT NULL)
    );

-- ── rw_message_revisions ────────────────────────────────────────────────────
ALTER TABLE rw_message_revisions
    ADD CONSTRAINT fk_rw_message_revisions_message FOREIGN KEY (message_id)
        REFERENCES rw_messages (message_id) ON DELETE CASCADE;

ALTER TABLE rw_message_revisions
    ADD CONSTRAINT fk_rw_message_revisions_editor FOREIGN KEY (edited_by)
        REFERENCES rw_users (user_id) ON DELETE RESTRICT;

ALTER TABLE rw_message_revisions
    ADD CONSTRAINT ck_rw_message_revisions_type CHECK (revision_type IN ('edit', 'delete'));

-- ── rw_message_embeddings ───────────────────────────────────────────────────
ALTER TABLE rw_message_embeddings
    ADD CONSTRAINT fk_rw_message_embeddings_message FOREIGN KEY (message_id)
        REFERENCES rw_messages (message_id) ON DELETE CASCADE;

-- ── rw_embedding_jobs ───────────────────────────────────────────────────────
ALTER TABLE rw_embedding_jobs
    ADD CONSTRAINT fk_rw_embedding_jobs_message FOREIGN KEY (message_id)
        REFERENCES rw_messages (message_id) ON DELETE CASCADE;

ALTER TABLE rw_embedding_jobs
    ADD CONSTRAINT ck_rw_embedding_jobs_status CHECK (status IN ('pending', 'done', 'failed'));

-- ── rw_refresh_tokens ───────────────────────────────────────────────────────
ALTER TABLE rw_refresh_tokens
    ADD CONSTRAINT fk_rw_refresh_tokens_user FOREIGN KEY (user_id)
        REFERENCES rw_users (user_id) ON DELETE CASCADE;

ALTER TABLE rw_refresh_tokens
    ADD CONSTRAINT fk_rw_refresh_tokens_rotated_from FOREIGN KEY (rotated_from)
        REFERENCES rw_refresh_tokens (token_id) ON DELETE SET NULL;

ALTER TABLE rw_refresh_tokens
    ADD CONSTRAINT uq_rw_refresh_tokens_hash UNIQUE (token_hash);

ALTER TABLE rw_refresh_tokens
    ADD CONSTRAINT ck_rw_refresh_tokens_expiry CHECK (expires_at > issued_at);

-- ── rw_copilot_queries ──────────────────────────────────────────────────────
ALTER TABLE rw_copilot_queries
    ADD CONSTRAINT fk_rw_copilot_queries_user FOREIGN KEY (user_id)
        REFERENCES rw_users (user_id) ON DELETE RESTRICT;

ALTER TABLE rw_copilot_queries
    ADD CONSTRAINT ck_rw_copilot_queries_tokens CHECK (tokens_used IS NULL OR tokens_used >= 0);

-- ── rw_copilot_citations ────────────────────────────────────────────────────
ALTER TABLE rw_copilot_citations
    ADD CONSTRAINT fk_rw_copilot_citations_query FOREIGN KEY (query_id)
        REFERENCES rw_copilot_queries (query_id) ON DELETE CASCADE;

ALTER TABLE rw_copilot_citations
    ADD CONSTRAINT fk_rw_copilot_citations_message FOREIGN KEY (message_id)
        REFERENCES rw_messages (message_id) ON DELETE RESTRICT;

-- Similitud coseno (1 - distancia coseno de pgvector, operador <=>): rango real [-1, 1],
-- no [0, 1] — una embedding poco relacionada con la consulta puede dar similitud negativa.
ALTER TABLE rw_copilot_citations
    ADD CONSTRAINT ck_rw_copilot_citations_score CHECK (
        similarity_score IS NULL OR (similarity_score >= -1 AND similarity_score <= 1)
    );
