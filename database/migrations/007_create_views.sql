-- Fase 3 — Vista de conversaciones del usuario (requisito explícito).
--
-- Se filtra por rw_current_user_id() directamente en el JOIN (no solo confía en RLS): la
-- vista es correcta para cualquier actor sin importar quién la consulte, siempre que el
-- backend haya fijado app.current_user_id con rw_set_current_user() al inicio de la
-- transacción (005_create_functions.sql).

CREATE VIEW rw_user_conversations AS
SELECT
    c.channel_id,
    c.name,
    c.channel_type,
    lm.message_id                AS last_message_id,
    lm.content                   AS last_message_content,
    lm.sender_id                 AS last_message_sender_id,
    lm.created_at                AS last_message_at,
    cm.last_read_message_id,
    (
        SELECT count(*)
          FROM rw_messages m2
         WHERE m2.channel_id = c.channel_id
           AND m2.is_deleted = FALSE
           AND m2.message_id > coalesce(cm.last_read_message_id, 0)
    )                             AS unread_count
FROM rw_channels c
JOIN rw_channel_members cm
  ON cm.channel_id = c.channel_id
 AND cm.user_id = rw_current_user_id()
LEFT JOIN LATERAL (
    SELECT message_id, content, sender_id, created_at
      FROM rw_messages m
     WHERE m.channel_id = c.channel_id
       AND m.is_deleted = FALSE
     ORDER BY m.message_id DESC
     LIMIT 1
) lm ON TRUE
ORDER BY lm.created_at DESC NULLS LAST;
