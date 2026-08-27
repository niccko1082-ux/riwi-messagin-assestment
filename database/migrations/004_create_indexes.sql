-- Fase 2 — DDL PostgreSQL. Índices de soporte + el índice único parcial requerido por la prueba.

-- Unicidad de email case-insensitive: reemplaza al UNIQUE(email) plano que se quitó de 003
-- (ver comentario ahí). 'Camilo@riwi.io' y 'camilo@riwi.io' colisionan como el mismo correo.
CREATE UNIQUE INDEX ux_rw_users_email_lower
    ON rw_users (lower(email));

-- Requisito explícito: al menos un índice único parcial. Evita encolar dos jobs de
-- re-embedding en vuelo para el mismo mensaje (patrón outbox sin duplicados), sin
-- restringir el histórico de jobs 'done'/'failed'.
CREATE UNIQUE INDEX ux_rw_embedding_jobs_pending_per_message
    ON rw_embedding_jobs (message_id)
    WHERE status = 'pending';

-- Búsqueda de mensajes con resaltado (Consulta 2): el tsvector se consulta con @@ y se
-- rankea/resalta con ts_rank / ts_headline; sin este índice GIN cada búsqueda sería un
-- seq scan sobre todos los mensajes visibles.
CREATE INDEX idx_rw_messages_search_vector
    ON rw_messages USING GIN (search_vector);

-- Recuperación de contexto para el copiloto (Consulta 3, RAG): búsqueda por similitud de
-- coseno sobre el embedding. HNSW (pgvector >= 0.5) no requiere fase de entrenamiento como
-- ivfflat y da mejor recall/latencia para el volumen esperado en esta prueba.
CREATE INDEX idx_rw_message_embeddings_hnsw
    ON rw_message_embeddings USING hnsw (embedding vector_cosine_ops);

-- Toda política RLS de canales/mensajes (Fase 3) resuelve "¿de qué canales es miembro este
-- usuario?" — sin este índice esa subconsulta sería un seq scan en cada fila evaluada.
CREATE INDEX idx_rw_channel_members_user
    ON rw_channel_members (user_id);

-- Rotación/revocación de refresh tokens: lookup por usuario en cada login/refresh.
CREATE INDEX idx_rw_refresh_tokens_user
    ON rw_refresh_tokens (user_id);

-- Historial de ediciones/eliminaciones de un mensaje (mismo patrón que los índices de
-- arriba: PostgreSQL no indexa automáticamente las columnas de FK).
CREATE INDEX idx_rw_message_revisions_message
    ON rw_message_revisions (message_id);

-- Consulta 4 (consumo acumulado del copiloto por usuario), típicamente agregada por rango
-- de fechas.
CREATE INDEX idx_rw_copilot_queries_user_created
    ON rw_copilot_queries (user_id, created_at);

-- Nota: la paginación por keyset de la Consulta 1 (historial de un canal) ya queda cubierta
-- por el índice implícito de la restricción UNIQUE (channel_id, message_id) declarada en
-- 002/003 — un índice adicional aquí sería redundante.
