package io.riwi.messaging.domain.model;

import java.util.Objects;
import java.util.UUID;

public record ChannelId(UUID value) {
    public ChannelId {
        Objects.requireNonNull(value, "channel id no puede ser null");
    }

    public static ChannelId of(String raw) {
        return new ChannelId(UUID.fromString(raw));
    }
}
