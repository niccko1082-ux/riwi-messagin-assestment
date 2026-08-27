package io.riwi.messaging.api.ws;

import io.riwi.messaging.api.security.CurrentActor;
import io.riwi.messaging.domain.model.ChannelId;
import io.riwi.messaging.domain.port.ChannelRepository;
import io.riwi.messaging.infrastructure.security.JwtTokenParser;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

/** Corrige un hallazgo de seguridad: /ws/** queda público a nivel HTTP (SockJS no siempre
 *  puede llevar headers en el handshake), así que la autenticación y autorización reales
 *  ocurren aquí, frame por frame:
 *  - CONNECT: exige un JWT válido en el header STOMP "Authorization"; sin él se rechaza la
 *    conexión completa.
 *  - SUBSCRIBE: exige que el actor autenticado sea miembro del canal del destino
 *    (/topic/channels/{channelId}) — sin esto, cualquier usuario autenticado podía
 *    suscribirse a canales ajenos y leer mensajes vía el evento de broadcast. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class StompAuthChannelInterceptor implements ChannelInterceptor {
    private static final String CHANNEL_TOPIC_PREFIX = "/topic/channels/";

    private final JwtTokenParser tokenParser;
    private final ChannelRepository channelRepository;

    public StompAuthChannelInterceptor(JwtTokenParser tokenParser, ChannelRepository channelRepository) {
        this.tokenParser = tokenParser;
        this.channelRepository = channelRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            CurrentActor actor = authenticate(accessor.getFirstNativeHeader("Authorization"));
            accessor.setUser(new UsernamePasswordAuthenticationToken(actor, null, List.of()));
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }

        return message;
    }

    private CurrentActor authenticate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new BadCredentialsException("falta token en el CONNECT del WebSocket");
        }
        String token = authorizationHeader.substring(7);
        return tokenParser.parse(token)
                .map(claims -> new CurrentActor(
                        new io.riwi.messaging.domain.model.UserId(claims.userId()),
                        claims.email(), claims.name(), claims.jobTitle()))
                .orElseThrow(() -> new BadCredentialsException("token inválido en el CONNECT del WebSocket"));
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(CHANNEL_TOPIC_PREFIX)) {
            throw new AccessDeniedException("destino de suscripción no permitido");
        }
        CurrentActor actor = extractActor(accessor);
        ChannelId channelId = ChannelId.of(destination.substring(CHANNEL_TOPIC_PREFIX.length()));
        if (!channelRepository.isMember(actor.userId(), channelId)) {
            throw new AccessDeniedException("el usuario no es miembro de este canal");
        }
    }

    private CurrentActor extractActor(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof CurrentActor actor) {
            return actor;
        }
        throw new BadCredentialsException("sesión WebSocket sin autenticar");
    }
}
