package io.riwi.messaging.application.user;

import io.riwi.messaging.domain.model.UserId;
import io.riwi.messaging.domain.port.UserRepository;

/** Autoservicio: rw_manage_user (Fase 3) exige actor == target, por eso este caso de uso no
 *  recibe un targetId aparte — siempre opera sobre el propio actor. */
public class UpdateMyProfileUseCase {
    private final UserRepository userRepository;

    public UpdateMyProfileUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void execute(UserId actorId, String firstName, String lastName, String jobTitle) {
        userRepository.manage(actorId, actorId, firstName, lastName, jobTitle, false);
    }

    public void deactivate(UserId actorId) {
        userRepository.manage(actorId, actorId, null, null, null, true);
    }
}
