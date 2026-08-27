package io.riwi.messaging.api.web;

import io.riwi.messaging.api.security.CurrentActor;
import io.riwi.messaging.api.web.dto.UpdateProfileRequest;
import io.riwi.messaging.api.web.dto.UserResponse;
import io.riwi.messaging.application.user.GetMyProfileUseCase;
import io.riwi.messaging.application.user.ListUsersUseCase;
import io.riwi.messaging.application.user.UpdateMyProfileUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final GetMyProfileUseCase getMyProfileUseCase;
    private final UpdateMyProfileUseCase updateMyProfileUseCase;
    private final ListUsersUseCase listUsersUseCase;

    public UserController(GetMyProfileUseCase getMyProfileUseCase, UpdateMyProfileUseCase updateMyProfileUseCase,
                           ListUsersUseCase listUsersUseCase) {
        this.getMyProfileUseCase = getMyProfileUseCase;
        this.updateMyProfileUseCase = updateMyProfileUseCase;
        this.listUsersUseCase = listUsersUseCase;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal CurrentActor actor) {
        return UserResponse.from(getMyProfileUseCase.execute(actor.userId()));
    }

    @PatchMapping("/me")
    public ResponseEntity<Void> updateMe(@AuthenticationPrincipal CurrentActor actor,
                                          @RequestBody UpdateProfileRequest request) {
        updateMyProfileUseCase.execute(actor.userId(), request.firstName(), request.lastName(), request.jobTitle());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deactivateMe(@AuthenticationPrincipal CurrentActor actor) {
        updateMyProfileUseCase.deactivate(actor.userId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<UserResponse> list(@RequestParam(required = false) String term,
                                    @RequestParam(defaultValue = "true") boolean onlyActive) {
        return listUsersUseCase.execute(term, onlyActive).stream().map(UserResponse::from).toList();
    }
}
