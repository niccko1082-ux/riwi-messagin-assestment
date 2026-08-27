package io.riwi.messaging.domain.model;

public enum ChannelType {
    DIRECT, GROUP;

    public String toDb() {
        return name().toLowerCase();
    }

    public static ChannelType fromDb(String raw) {
        return ChannelType.valueOf(raw.toUpperCase());
    }
}
