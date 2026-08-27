package io.riwi.messaging.domain.model;

import java.time.Instant;

public record User(
        UserId id,
        String firstName,
        String lastName,
        String email,
        String passwordHash,
        String jobTitle,
        boolean active,
        Instant createdAt
) {
    public String fullName() {
        return firstName + " " + lastName;
    }
}
