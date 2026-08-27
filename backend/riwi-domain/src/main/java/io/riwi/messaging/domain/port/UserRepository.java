package io.riwi.messaging.domain.port;

import io.riwi.messaging.domain.model.User;
import io.riwi.messaging.domain.model.UserId;

import java.util.List;
import java.util.Optional;

/** Puerto (Dependency Inversion): application depende de esta interfaz, no del driver JDBC.
 *  Implementado en riwi-infrastructure. */
public interface UserRepository {
    Optional<User> findByEmail(String email);

    Optional<User> findById(UserId id);

    /** CALL rw_query_users (procedimiento, Fase 3). */
    List<User> search(String term, boolean onlyActive);

    /** CALL rw_manage_user (procedimiento, autoservicio, Fase 3). */
    void manage(UserId actorId, UserId targetId, String firstName, String lastName,
                String jobTitle, boolean deactivate);
}
