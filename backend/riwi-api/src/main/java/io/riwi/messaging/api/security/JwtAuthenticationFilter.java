package io.riwi.messaging.api.security;

import io.riwi.messaging.domain.model.UserId;
import io.riwi.messaging.infrastructure.security.JwtTokenParser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Única fuente del actor autenticado: lee el Bearer token, nunca el cuerpo de la petición
 *  (requisito explícito). Rutas públicas (login/refresh/docs) simplemente no traen token
 *  válido y quedan sin autenticar — SecurityConfig decide si eso basta o no para la ruta. */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenParser tokenParser;

    public JwtAuthenticationFilter(JwtTokenParser tokenParser) {
        this.tokenParser = tokenParser;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            tokenParser.parse(token).ifPresent(claims -> {
                CurrentActor actor = new CurrentActor(
                        new UserId(claims.userId()), claims.email(), claims.name(), claims.jobTitle());
                var authentication = new UsernamePasswordAuthenticationToken(actor, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }
        chain.doFilter(request, response);
    }
}
