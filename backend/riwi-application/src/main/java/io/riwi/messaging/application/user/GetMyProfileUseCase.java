package io.riwi.messaging.application.user;

import io.riwi.messaging.domain.exception.NotFoundException;
import io.riwi.messaging.domain.model.User;
import io.riwi.messaging.domain.model.UserId;
import io.riwi.messaging.domain.port.UserRepository;

public class GetMyProfileUseCase {
    private final UserRepository userRepository;

    public GetMyProfileUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User execute(UserId actorId) {
        return userRepository.findById(actorId)
                .orElseThrow(() -> new NotFoundException("usuario no encontrado"));
    }
}
