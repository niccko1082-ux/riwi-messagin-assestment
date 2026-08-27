package io.riwi.messaging.api.ws;

import io.riwi.messaging.domain.model.ChannelId;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Empuja eventos de mensajería a los suscriptores del canal. Vive en api (no en
 *  application/domain): el transporte en tiempo real es un detalle de entrega, no una regla
 *  de negocio — un caso de uso no sabe ni le importa que WebSocket exista. */
@Component
public class ChannelBroadcaster {
    private final SimpMessagingTemplate messagingTemplate;

    public ChannelBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publish(ChannelId channelId, Object event) {
        messagingTemplate.convertAndSend("/topic/channels/" + channelId.value(), event);
    }
}
