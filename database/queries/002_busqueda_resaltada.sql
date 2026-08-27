-- Consulta 2 (requisito 11): búsqueda de mensajes con resaltado del término encontrado
-- (ts_headline), paginada por keyset igual que la Consulta 1.
--
-- Implementación real: JdbcMessageRepository.search()
-- (backend/riwi-infrastructure/.../persistence/JdbcMessageRepository.java)
--
-- El frontend parsea <mark>/</mark> manualmente (nunca dangerouslySetInnerHTML) para evitar
-- XSS almacenado con contenido de mensajes ajenos.
-- Validado contra Postgres real:
--   psql -U rw_app -d bd_nombre_apellido_clan -v ON_ERROR_STOP=1 -f 002_busqueda_resaltada.sql

BEGIN;
SELECT rw_set_current_user('00000000-0000-0000-0000-000000000000'); -- reemplazar por el user_id real

SELECT message_id, channel_id,
       ts_headline('spanish', content, plainto_tsquery('spanish', 'PostgreSQL'), -- reemplazar término
                   'StartSel=<mark>,StopSel=</mark>') AS highlighted,
       ts_rank(search_vector, plainto_tsquery('spanish', 'PostgreSQL')) AS rank
  FROM rw_messages
 WHERE search_vector @@ plainto_tsquery('spanish', 'PostgreSQL')
   AND is_deleted = FALSE
   AND (NULL::bigint IS NULL OR message_id < NULL::bigint) -- reemplazar NULL por el cursor
 ORDER BY message_id DESC
 LIMIT 21;
COMMIT;
