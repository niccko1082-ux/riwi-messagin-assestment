package io.riwi.messaging.application.user;

import io.riwi.messaging.domain.model.User;
import io.riwi.messaging.domain.port.UserRepository;

import java.util.List;

/** Directorio interno de usuarios (CALL rw_query_users, Fase 3). */
public class ListUsersUseCase {
    private final UserRepository userRepository;

    public ListUsersUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> execute(String term, boolean onlyActive) {
        return userRepository.search(term, onlyActive);
    }
}
