-- Fase 3 — Rol de aplicación + Row Level Security. Requisito no negociable de la prueba:
-- ningún usuario puede leer, buscar o consultar (vía copiloto) contenido al que no tiene
-- acceso.
--
-- Invocación esperada (la contraseña nunca queda hardcodeada en el archivo):
--   psql -v app_password="$RW_APP_PASSWORD" -f 008_create_rls.sql
--
-- Modelo de privilegios: rw_app es un rol de LOGIN sin BYPASSRLS. Solo recibe SELECT
-- (filtrado por RLS) sobre las tablas y EXECUTE sobre las funciones/procedimientos de
-- 005/009 — nunca INSERT/UPDATE/DELETE directo. Todo cambio pasa por una función
-- SECURITY DEFINER que ya valida permisos explícitamente (005), así que RLS aquí protege
-- sobre todo las lecturas: incluso una consulta ad-hoc mal escrita en el backend no puede
-- filtrar canales/mensajes ajenos.
--
-- No se usa FORCE ROW LEVEL SECURITY: las funciones SECURITY DEFINER corren como el dueño
-- de las funciones (no como rw_app) precisamente para poder validar permisos con lógica de
-- negocio explícita en vez de depender de las políticas — forzar RLS también sobre el dueño
-- rompería esa validación manual.

CREATE ROLE rw_app LOGIN PASSWORD :'app_password' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;

GRANT USAGE ON SCHEMA public TO rw_app;

-- Default-deny: PostgreSQL otorga EXECUTE a PUBLIC automáticamente en toda función nueva
-- (a diferencia de las tablas). Sin este REVOKE explícito, rw_app podría ejecutar
-- rw_my_channel_ids(), rw_record_embedding() o rw_fail_embedding_job() aunque nunca se les
-- otorgue EXECUTE más abajo — el modelo de privilegios de este archivo sería una lista de
-- permisos decorativa, no la que realmente aplica. Se revoca por firma exacta, función por
-- función (no "ALL FUNCTIONS IN SCHEMA public"): ese enfoque más amplio también revoca
-- EXECUTE sobre las funciones internas de pgvector/pgcrypto instaladas en el mismo schema
-- public, lo que rompería cualquier CAST explícito a vector (::vector) que rw_app necesite
-- fuera de estas funciones.
REVOKE EXECUTE ON FUNCTION rw_set_current_user(uuid) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION rw_current_user_id() FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION rw_my_channel_ids() FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION rw_send_message(uuid, text) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION rw_edit_message(bigint, text) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION rw_delete_message(bigint) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION rw_copilot_search_context(vector, integer) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION rw_log_copilot_query(text, text, integer, boolean, text, bigint[], numeric[]) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION rw_record_embedding(bigint, vector, text) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION rw_fail_embedding_job(bigint) FROM PUBLIC;

GRANT SELECT ON
    rw_users, rw_channels, rw_channel_members, rw_messages, rw_message_revisions,
    rw_message_embeddings, rw_copilot_queries, rw_copilot_citations, rw_user_conversations
TO rw_app;

GRANT EXECUTE ON FUNCTION rw_set_current_user(uuid) TO rw_app;
GRANT EXECUTE ON FUNCTION rw_current_user_id() TO rw_app;
-- rw_app SÍ necesita EXECUTE aquí, aunque nunca la llame directamente: las políticas RLS de
-- rw_channels/rw_channel_members/rw_messages de más abajo la invocan dentro del USING, y esa
-- evaluación corre con los privilegios de quien ejecuta el SELECT (rw_app) — SECURITY
-- DEFINER solo cambia los privilegios DENTRO de la función, no si el llamador puede
-- invocarla. Sin este GRANT, cualquier SELECT de rw_app sobre esas tres tablas fallaría con
-- "permission denied for function rw_my_channel_ids".
GRANT EXECUTE ON FUNCTION rw_my_channel_ids() TO rw_app;
GRANT EXECUTE ON FUNCTION rw_send_message(uuid, text) TO rw_app;
GRANT EXECUTE ON FUNCTION rw_edit_message(bigint, text) TO rw_app;
GRANT EXECUTE ON FUNCTION rw_delete_message(bigint) TO rw_app;
GRANT EXECUTE ON FUNCTION rw_copilot_search_context(vector, integer) TO rw_app;
GRANT EXECUTE ON FUNCTION rw_log_copilot_query(text, text, integer, boolean, text, bigint[], numeric[]) TO rw_app;
-- rw_record_embedding / rw_fail_embedding_job NO se otorgan a rw_app: las llama el worker
-- de embeddings del backend con sus propias credenciales internas, no una sesión de usuario.

-- ── rw_users: SIN RLS a propósito ──────────────────────────────────────────
-- El login necesita poder leer cualquier fila por email ANTES de que exista un actor
-- autenticado (app.current_user_id todavía no está fijado en ese punto del flujo) — igual
-- que rw_refresh_tokens (búsqueda por token_hash para rotar/revocar). Ninguna de las dos
-- tablas está en el alcance explícito "RLS sobre canales y mensajes" del requisito.

-- ── rw_channels ─────────────────────────────────────────────────────────────
ALTER TABLE rw_channels ENABLE ROW LEVEL SECURITY;

-- Usa el helper rw_my_channel_ids() (005) en vez de una subconsulta inline contra
-- rw_channel_members: necesario por la política de rw_channel_members de abajo (que también
-- depende del helper) — de lo contrario, evaluar esta política dispararía la evaluación de
-- la política de rw_channel_members, que a su vez... ver el comentario de esa política.
CREATE POLICY sel_rw_channels ON rw_channels
    FOR SELECT
    USING (channel_id IN (SELECT rw_my_channel_ids()));

-- ── rw_channel_members ──────────────────────────────────────────────────────
-- Sin esto, cualquiera con SELECT sobre la tabla podría enumerar la membresía de canales
-- ajenos consultándola directamente (aunque rw_channels/rw_messages ya estén protegidas).
--
-- IMPORTANTE: la política NO puede subconsultar rw_channel_members directamente (p. ej.
-- "channel_id IN (SELECT channel_id FROM rw_channel_members WHERE user_id = ...)"): esa
-- subconsulta lee la misma tabla que la política protege, así que PostgreSQL necesitaría
-- volver a evaluar esta misma política para decidir qué filas son visibles en la
-- subconsulta — recursión infinita ("infinite recursion detected in policy for relation
-- rw_channel_members"). Se usa rw_my_channel_ids(), una función SECURITY DEFINER (005): al
-- ejecutarse con los privilegios del owner de la tabla (que no tiene FORCE ROW LEVEL
-- SECURITY activado), su lectura interna de rw_channel_members omite RLS por completo,
-- rompiendo el ciclo.
ALTER TABLE rw_channel_members ENABLE ROW LEVEL SECURITY;

CREATE POLICY sel_rw_channel_members ON rw_channel_members
    FOR SELECT
    USING (channel_id IN (SELECT rw_my_channel_ids()));

-- ── rw_messages ─────────────────────────────────────────────────────────────
ALTER TABLE rw_messages ENABLE ROW LEVEL SECURITY;

CREATE POLICY sel_rw_messages ON rw_messages
    FOR SELECT
    USING (channel_id IN (SELECT rw_my_channel_ids()));

-- ── rw_message_revisions / rw_message_embeddings ────────────────────────────
-- Heredan la visibilidad de rw_messages: el EXISTS reutiliza la política de arriba (la
-- subconsulta contra rw_messages ya viene filtrada por RLS).
ALTER TABLE rw_message_revisions ENABLE ROW LEVEL SECURITY;

CREATE POLICY sel_rw_message_revisions ON rw_message_revisions
    FOR SELECT
    USING (EXISTS (SELECT 1 FROM rw_messages m WHERE m.message_id = rw_message_revisions.message_id));

ALTER TABLE rw_message_embeddings ENABLE ROW LEVEL SECURITY;

CREATE POLICY sel_rw_message_embeddings ON rw_message_embeddings
    FOR SELECT
    USING (EXISTS (SELECT 1 FROM rw_messages m WHERE m.message_id = rw_message_embeddings.message_id));

-- ── rw_copilot_queries / rw_copilot_citations ───────────────────────────────
-- Un usuario solo ve su propio historial de consultas al copiloto.
ALTER TABLE rw_copilot_queries ENABLE ROW LEVEL SECURITY;

CREATE POLICY sel_rw_copilot_queries ON rw_copilot_queries
    FOR SELECT
    USING (user_id = rw_current_user_id());

ALTER TABLE rw_copilot_citations ENABLE ROW LEVEL SECURITY;

CREATE POLICY sel_rw_copilot_citations ON rw_copilot_citations
    FOR SELECT
    USING (EXISTS (SELECT 1 FROM rw_copilot_queries q WHERE q.query_id = rw_copilot_citations.query_id));
