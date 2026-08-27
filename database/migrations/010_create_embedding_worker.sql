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
