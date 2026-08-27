-- Consulta 4 (requisito 11): consumo acumulado del copiloto por usuario.
-- Doble filtrado por actor: el WHERE explícito y, como respaldo, la política RLS
-- sel_rw_copilot_queries (user_id = rw_current_user_id()) — un usuario solo ve su propio
-- historial de consumo, nunca el de otro.
--
-- Implementación real: JdbcCopilotRepository.getUsageSummary()
-- (backend/riwi-infrastructure/.../persistence/JdbcCopilotRepository.java)
-- Validado contra Postgres real:
--   psql -U rw_app -d bd_nombre_apellido_clan -v ON_ERROR_STOP=1 -f 004_uso_copiloto.sql

BEGIN;
SELECT rw_set_current_user('00000000-0000-0000-0000-000000000000'); -- reemplazar por el user_id real

SELECT count(*) AS total_queries,
       coalesce(sum(tokens_used), 0) AS total_tokens,
       max(created_at) AS last_query_at
  FROM rw_copilot_queries
 WHERE user_id = '00000000-0000-0000-0000-000000000000'; -- mismo user_id de arriba
COMMIT;
