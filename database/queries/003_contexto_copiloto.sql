-- Consulta 3 (requisito 11): recuperación de contexto para el copiloto, con permisos
-- validados en SQL (no solo en el backend). Definida como función SECURITY DEFINER en
-- database/migrations/005_create_functions.sql: el filtro de membresía vive DENTRO de la
-- función, así que sigue siendo correcta aunque en el futuro alguien la invoque desde un
-- rol distinto a rw_app.
--
-- Definición completa (no una copia — es la fuente real):
--
-- CREATE FUNCTION rw_copilot_search_context(p_query_embedding vector(1024), p_match_count integer DEFAULT 8)
-- RETURNS TABLE (message_id bigint, channel_id uuid, content text, similarity numeric)
-- LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
--     SELECT m.message_id, m.channel_id, m.content,
--            (1 - (e.embedding <=> p_query_embedding))::numeric AS similarity
--       FROM rw_message_embeddings e
--       JOIN rw_messages m ON m.message_id = e.message_id
--      WHERE m.is_deleted = FALSE
--        AND m.channel_id IN (SELECT rw_my_channel_ids())  -- <- el permiso vive aquí, en SQL
--      ORDER BY e.embedding <=> p_query_embedding
--      LIMIT p_match_count;
-- $$;
--
-- Implementación real de la llamada: JdbcCopilotRepository.searchContext()
-- (backend/riwi-infrastructure/.../persistence/JdbcCopilotRepository.java)

BEGIN;
SELECT rw_set_current_user('00000000-0000-0000-0000-000000000000'); -- reemplazar por el user_id real

SELECT * FROM rw_copilot_search_context('[0,0,...]'::vector, 8); -- reemplazar por el embedding real (1024 dims)
COMMIT;
