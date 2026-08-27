-- Consulta 1 (requisito 11): historial de mensajes de un canal, paginado por keyset
-- (nunca OFFSET, prohibido por la prueba). RLS (008_create_rls.sql) filtra por membresía —
-- sin ella, este SELECT devolvería mensajes de canales ajenos al actor.
--
-- Implementación real: JdbcMessageRepository.history()
-- (backend/riwi-infrastructure/.../persistence/JdbcMessageRepository.java)
--
-- rw_set_current_user usa SET LOCAL: debe correr en la MISMA transacción que la consulta
-- (por eso BEGIN/COMMIT explícitos abajo), o el actor no queda fijado para RLS.
-- Validado contra Postgres real:
--   psql -U rw_app -d bd_nombre_apellido_clan -v ON_ERROR_STOP=1 -f 001_historial_keyset.sql

BEGIN;
SELECT rw_set_current_user('00000000-0000-0000-0000-000000000000'); -- reemplazar por el user_id real

-- :cursor = último message_id visto por el cliente (NULL en la primera página).
-- limit+1 (21 en vez de 20) para saber si hay página siguiente sin un segundo roundtrip.
SELECT message_id, channel_id, sender_id, content, status, edited_at, created_at
  FROM rw_messages
 WHERE channel_id = '00000000-0000-0000-0000-000000000000' -- reemplazar por el channel_id real
   AND (NULL::bigint IS NULL OR message_id < NULL::bigint) -- reemplazar NULL por el cursor
 ORDER BY message_id DESC
 LIMIT 21;
COMMIT;
