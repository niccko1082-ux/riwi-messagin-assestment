package io.riwi.messaging.api.web.dto;

import io.riwi.messaging.domain.model.User;

import java.time.Instant;
import java.util.UUID;

/** Nunca incluye passwordHash. */
public record UserResponse(
        UUID id, String firstName, String lastName, String email, String jobTitle,
        boolean active, Instant createdAt
) {
    public static UserResponse from(User u) {
        return new UserResponse(u.id().value(), u.firstName(), u.lastName(), u.email(), u.jobTitle(),
                u.active(), u.createdAt());
    }
}
