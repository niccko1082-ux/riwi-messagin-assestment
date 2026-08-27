package io.riwi.messaging.api.web;

import io.riwi.messaging.api.security.CurrentActor;
import io.riwi.messaging.api.web.dto.*;
import io.riwi.messaging.api.ws.ChannelBroadcaster;
import io.riwi.messaging.application.messaging.*;
import io.riwi.messaging.domain.model.ChannelId;
import io.riwi.messaging.domain.model.MessageId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;

/** Patrón de controller delgado (requisito explícito): valida entrada (Bean Validation),
 *  invoca el caso de uso, mapea el resultado a DTO. Ninguna regla de negocio vive aquí. */
@RestController
@RequestMapping("/api")
public class MessageController {
    private final SendMessageUseCase sendMessageUseCase;
    private final EditMessageUseCase editMessageUseCase;
    private final DeleteMessageUseCase deleteMessageUseCase;
    private final GetChannelHistoryUseCase getChannelHistoryUseCase;
    private final ChannelBroadcaster broadcaster;

    public MessageController(SendMessageUseCase sendMessageUseCase, EditMessageUseCase editMessageUseCase,
                              DeleteMessageUseCase deleteMessageUseCase,
                              GetChannelHistoryUseCase getChannelHistoryUseCase, ChannelBroadcaster broadcaster) {
        this.sendMessageUseCase = sendMessageUseCase;
        this.editMessageUseCase = editMessageUseCase;
        this.deleteMessageUseCase = deleteMessageUseCase;
        this.getChannelHistoryUseCase = getChannelHistoryUseCase;
        this.broadcaster = broadcaster;
    }

    @PostMapping("/channels/{channelId}/messages")
    public ResponseEntity<MessageResponse> send(@AuthenticationPrincipal CurrentActor actor,
                                                 @PathVariable String channelId,
                                                 @Valid @RequestBody SendMessageRequest request) {
        ChannelId channel = ChannelId.of(channelId);
        MessageId id = sendMessageUseCase.execute(actor.userId(), channel, request.content());

        MessageResponse body = new MessageResponse(id.value(), channel.value(), actor.userId().value(),
                request.content(), "sent", null, Instant.now());
        broadcaster.publish(channel, new MessageEvent(MessageEventType.SENT, id.value(), body));

        return ResponseEntity.created(URI.create("/api/messages/" + id.value())).body(body);
    }

    // channelId NUNCA se toma del cliente aquí (hallazgo de seguridad corregido): los casos
    // de uso devuelven el channelId real del mensaje, evitando spoofear a qué topic de
    // WebSocket se transmite el evento.
    @PatchMapping("/messages/{messageId}")
    public ResponseEntity<Void> edit(@AuthenticationPrincipal CurrentActor actor,
                                      @PathVariable long messageId,
                                      @Valid @RequestBody EditMessageRequest request) {
        ChannelId channel = editMessageUseCase.execute(actor.userId(), new MessageId(messageId), request.content());

        MessageResponse body = new MessageResponse(messageId, channel.value(), actor.userId().value(),
                request.content(), "edited", Instant.now(), null);
        broadcaster.publish(channel, new MessageEvent(MessageEventType.EDITED, messageId, body));

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal CurrentActor actor,
                                        @PathVariable long messageId) {
        ChannelId channel = deleteMessageUseCase.execute(actor.userId(), new MessageId(messageId));
        broadcaster.publish(channel, new MessageEvent(MessageEventType.DELETED, messageId, null));

        return ResponseEntity.noContent().build();
    }

    // Consulta 1: historial por keyset. cursor=null -> página más reciente.
    @GetMapping("/channels/{channelId}/messages")
    public KeysetPageResponse<MessageResponse> history(@AuthenticationPrincipal CurrentActor actor,
                                                         @PathVariable String channelId,
                                                         @RequestParam(required = false) Long cursor,
                                                         @RequestParam(defaultValue = "30") int limit) {
        var page = getChannelHistoryUseCase.execute(actor.userId(), ChannelId.of(channelId), cursor, limit);
        return KeysetPageResponse.from(page, MessageResponse::from);
    }
}
