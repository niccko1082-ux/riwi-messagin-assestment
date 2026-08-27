package io.riwi.messaging.domain.model;

import java.util.Objects;
import java.util.UUID;

public record UserId(UUID value) {
    public UserId {
        Objects.requireNonNull(value, "user id no puede ser null");
    }

    public static UserId of(String raw) {
        return new UserId(UUID.fromString(raw));
    }
}
