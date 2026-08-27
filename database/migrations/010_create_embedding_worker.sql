-- Fase 5 — Rol de servicio para el worker de embeddings (proceso interno, no una sesión de
-- usuario). BYPASSRLS aquí es intencional y distinto del requisito "sin BYPASSRLS" de
-- 008_create_rls.sql: ese requisito aplica al rol de APLICACIÓN que atiende peticiones de
-- usuarios autenticados (rw_app); el worker no actúa en nombre de ningún usuario — solo
-- calcula embeddings sobre mensajes ya persistidos y no tiene forma de fijar
-- app.current_user_id con sentido (no hay actor). Sin BYPASSRLS necesitaría duplicar la
-- lógica de acceso en cada función, sin ganar nada en seguridad real.
--
-- Invocación: psql -v worker_password="$RW_WORKER_PASSWORD" -f 010_create_embedding_worker.sql

CREATE ROLE rw_worker LOGIN PASSWORD :'worker_password' NOSUPERUSER NOCREATEDB NOCREATEROLE BYPASSRLS;

GRANT USAGE ON SCHEMA public TO rw_worker;
GRANT SELECT ON rw_embedding_jobs, rw_messages TO rw_worker;
GRANT EXECUTE ON FUNCTION rw_record_embedding(bigint, vector, text) TO rw_worker;
GRANT EXECUTE ON FUNCTION rw_fail_embedding_job(bigint) TO rw_worker;

-- Un mensaje puede borrarse (soft delete) entre el encolado del job y el poll del worker;
-- rw_copilot_search_context ya excluye is_deleted, así que embeberlo sería una llamada pagada
-- a NVIDIA sin uso. SECURITY DEFINER en vez de un GRANT UPDATE directo sobre
-- rw_embedding_jobs: mantiene el mismo patrón que rw_record_embedding/rw_fail_embedding_job
-- (rw_worker nunca escribe tablas directamente, solo vía funciones acotadas).
CREATE FUNCTION rw_fail_deleted_message_embedding_jobs()
RETURNS void
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    UPDATE rw_embedding_jobs j
       SET status = 'failed', processed_at = now()
      FROM rw_messages m
     WHERE m.message_id = j.message_id
       AND j.status = 'pending'
       AND m.is_deleted = TRUE;
$$;

GRANT EXECUTE ON FUNCTION rw_fail_deleted_message_embedding_jobs() TO rw_worker;
