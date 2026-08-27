-- Fase 2 — DDL PostgreSQL. Tablas base (columnas, PK, DEFAULT). FKs/UNIQUE/CHECK van en 003,
-- índices en 004. Todas las fechas son timestamptz (instante UTC, independiente de la zona
-- horaria de la sesión) — nunca se usa `AT TIME ZONE` sobre `now()`, eso convertiría el valor
-- a `timestamp without time zone` y reintroduciría ambigüedad de zona horaria.

CREATE TABLE rw_users (
    user_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name     TEXT NOT NULL,
    last_name      TEXT NOT NULL,
    email          TEXT NOT NULL,
    password_hash  TEXT NOT NULL,
    job_title      TEXT NOT NULL,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE rw_channels (
    channel_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           TEXT,
    channel_type   TEXT NOT NULL,
    created_by     UUID NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Tabla puente: la membresía es la base de las políticas RLS de la Fase 3.
CREATE TABLE rw_channel_members (
    channel_id             UUID NOT NULL,
    user_id                UUID NOT NULL,
    role_in_channel        TEXT NOT NULL DEFAULT 'member',
    last_read_message_id   BIGINT,
    joined_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (channel_id, user_id)
);

CREATE TABLE rw_messages (
    message_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    channel_id     UUID NOT NULL,
    sender_id      UUID NOT NULL,
    content        TEXT NOT NULL,
    is_deleted     BOOLEAN NOT NULL DEFAULT FALSE,
    edited_at      TIMESTAMPTZ,
    deleted_at     TIMESTAMPTZ,
    -- Derivado siempre de is_deleted/edited_at por PostgreSQL: nunca puede desincronizarse
    -- (hallazgo del code review de la Fase 1, ver DECISIONS.md).
    status         TEXT GENERATED ALWAYS AS (
                       CASE
                           WHEN is_deleted THEN 'deleted'
                           WHEN edited_at IS NOT NULL THEN 'edited'
                           ELSE 'sent'
                       END
                   ) STORED,
    -- Poblado por trigger en la Fase 3 (006_create_triggers.sql); nullable aquí a propósito
    -- para no acoplar esta migración al trigger que todavía no existe.
    search_vector  TSVECTOR,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Habilita la FK compuesta de rw_channel_members.last_read_message_id (003): garantiza
    -- en el propio motor que un "último leído" pertenece al canal de la membresía.
    CONSTRAINT uq_rw_messages_channel_message UNIQUE (channel_id, message_id)
);

-- Historial de ediciones/eliminaciones: preserva el contenido original antes de cada cambio,
-- para que un fallo a mitad de una operación no deje al mensaje en un estado inconsistente.
CREATE TABLE rw_message_revisions (
    revision_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    message_id         BIGINT NOT NULL,
    previous_content   TEXT NOT NULL,
    revision_type      TEXT NOT NULL,
    edited_by          UUID NOT NULL,
    edited_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Vector para RAG. Se llena de forma asíncrona (job en rw_embedding_jobs) porque requiere
-- llamar al proveedor de IA externo — nunca dentro de una transacción de PostgreSQL.
CREATE TABLE rw_message_embeddings (
    message_id       BIGINT PRIMARY KEY,
    embedding        VECTOR(1024) NOT NULL,
    embedding_model  TEXT NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Outbox: encolado por el trigger de la Fase 3, procesado por el backend.
CREATE TABLE rw_embedding_jobs (
    job_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    message_id    BIGINT NOT NULL,
    status        TEXT NOT NULL DEFAULT 'pending',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at  TIMESTAMPTZ
);

-- El token en sí nunca se guarda; solo su hash. La rotación se encadena vía rotated_from.
CREATE TABLE rw_refresh_tokens (
    token_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL,
    token_hash    TEXT NOT NULL,
    rotated_from  UUID,
    issued_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ NOT NULL,
    revoked_at    TIMESTAMPTZ
);

-- Registro de uso del copiloto: soporta la Consulta 4 (consumo acumulado por usuario) y
-- ancla las citas a mensajes fuente (rw_copilot_citations).
CREATE TABLE rw_copilot_queries (
    query_id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                  UUID NOT NULL,
    question                 TEXT NOT NULL,
    answer                   TEXT,
    tokens_used              INTEGER,
    had_sufficient_context   BOOLEAN NOT NULL DEFAULT FALSE,
    system_prompt_version    TEXT NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE rw_copilot_citations (
    query_id           BIGINT NOT NULL,
    message_id         BIGINT NOT NULL,
    similarity_score   NUMERIC,
    PRIMARY KEY (query_id, message_id)
);
