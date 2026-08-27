package io.riwi.messaging.api.web;

import io.riwi.messaging.api.web.dto.AuthResponse;
import io.riwi.messaging.api.web.dto.LoginRequest;
import io.riwi.messaging.api.web.dto.RefreshRequest;
import io.riwi.messaging.application.auth.LoginUseCase;
import io.riwi.messaging.application.auth.RefreshTokenUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Rutas públicas (ver SecurityConfig.PUBLIC_PATHS): sin actor autenticado todavía. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;

    public AuthController(LoginUseCase loginUseCase, RefreshTokenUseCase refreshTokenUseCase) {
        this.loginUseCase = loginUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return AuthResponse.from(loginUseCase.execute(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return AuthResponse.from(refreshTokenUseCase.execute(request.refreshToken()));
    }
}
