package io.riwi.messaging.domain.port;

import io.riwi.messaging.domain.model.*;

public interface MessageRepository {
    /** SELECT rw_send_message (función, Fase 3): valida membresía en BD. */
    MessageId send(UserId actorId, ChannelId channelId, String content);

    /** SELECT rw_edit_message. */
    void edit(UserId actorId, MessageId messageId, String newContent);

    /** SELECT rw_delete_message (soft delete). */
    void delete(UserId actorId, MessageId messageId);

    /** Consulta 1: historial por canal, keyset (sin OFFSET). cursor=null trae la página más
     *  reciente. */
    KeysetPage<Message> history(UserId actorId, ChannelId channelId, Long cursor, int limit);

    /** Consulta 2: búsqueda con resaltado, keyset. */
    KeysetPage<MessageSearchResult> search(UserId actorId, String term, Long cursor, int limit);
}
