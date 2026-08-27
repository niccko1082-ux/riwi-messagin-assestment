package io.riwi.messaging.api.config;

import io.riwi.messaging.api.ws.StompAuthChannelInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/** Mensajería en tiempo real (criterio de aceptación explícito). El cliente se suscribe a
 *  /topic/channels/{channelId}; el backend publica ahí al enviar/editar/eliminar un mensaje
 *  (ver MessageController). No hay STOMP entrante desde el cliente: los cambios de estado
 *  siempre pasan por la API REST (que ya valida permisos en BD), el socket es solo push.
 *
 *  /ws/** queda público a nivel HTTP (SecurityConfig) porque SockJS no siempre puede llevar
 *  headers en el handshake inicial — la autenticación (CONNECT) y autorización por canal
 *  (SUBSCRIBE) real ocurren en StompAuthChannelInterceptor, registrado abajo. */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final StompAuthChannelInterceptor authInterceptor;
    private final String[] allowedOrigins;

    public WebSocketConfig(StompAuthChannelInterceptor authInterceptor,
                            @Value("${riwi.cors.allowed-origins:http://localhost:5173}") String[] allowedOrigins) {
        this.authInterceptor = authInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins).withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
