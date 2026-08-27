package io.riwi.messaging.api.security;

import io.riwi.messaging.domain.model.UserId;

/** Principal de Spring Security construido desde los claims del JWT (nunca del cuerpo de la
 *  petición). Los controllers lo obtienen vía @AuthenticationPrincipal. */
public record CurrentActor(UserId userId, String email, String name, String jobTitle) {
}
