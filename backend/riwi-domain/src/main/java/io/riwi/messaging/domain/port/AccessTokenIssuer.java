package io.riwi.messaging.domain.port;

import io.riwi.messaging.domain.model.AccessToken;
import io.riwi.messaging.domain.model.User;

/** DIP: emisión de JWT es un detalle de infraestructura (firma, expiración, claims). */
public interface AccessTokenIssuer {
    AccessToken issue(User user);
}
