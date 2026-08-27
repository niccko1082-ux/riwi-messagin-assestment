package io.riwi.messaging.domain.model;

import java.time.Instant;

public record AccessToken(String value, Instant expiresAt) {
}
