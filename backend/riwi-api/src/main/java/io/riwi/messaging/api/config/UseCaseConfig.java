package io.riwi.messaging.api.config;

import io.riwi.messaging.application.auth.LoginUseCase;
import io.riwi.messaging.application.auth.RefreshTokenUseCase;
import io.riwi.messaging.application.copilot.AskCopilotUseCase;
import io.riwi.messaging.application.copilot.GetCopilotUsageUseCase;
import io.riwi.messaging.application.copilot.ProcessEmbeddingJobsUseCase;
import io.riwi.messaging.application.messaging.*;
import io.riwi.messaging.application.user.GetMyProfileUseCase;
import io.riwi.messaging.application.user.ListUsersUseCase;
import io.riwi.messaging.application.user.UpdateMyProfileUseCase;
import io.riwi.messaging.domain.port.*;
import io.riwi.messaging.infrastructure.security.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Composition root de los casos de uso: son POJOs sin anotaciones de Spring (Clean
 *  Architecture — application no depende del framework), así que su instanciación con los
 *  adaptadores concretos vive aquí, no en las propias clases. */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class UseCaseConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public LoginUseCase loginUseCase(UserRepository userRepository, PasswordHasher passwordHasher,
                                      AccessTokenIssuer accessTokenIssuer, RefreshTokenRepository refreshTokenRepository,
                                      TokenHasher tokenHasher, JwtProperties jwtProperties, Clock clock) {
        return new LoginUseCase(userRepository, passwordHasher, accessTokenIssuer, refreshTokenRepository,
                tokenHasher, jwtProperties.refreshTokenTtl(), clock);
    }

    @Bean
    public RefreshTokenUseCase refreshTokenUseCase(RefreshTokenRepository refreshTokenRepository,
                                                     UserRepository userRepository, TokenHasher tokenHasher,
                                                     AccessTokenIssuer accessTokenIssuer, JwtProperties jwtProperties,
                                                     Clock clock) {
        return new RefreshTokenUseCase(refreshTokenRepository, userRepository, tokenHasher, accessTokenIssuer,
                jwtProperties.refreshTokenTtl(), clock);
    }

    @Bean
    public SendMessageUseCase sendMessageUseCase(MessageRepository messageRepository) {
        return new SendMessageUseCase(messageRepository);
    }

    @Bean
    public EditMessageUseCase editMessageUseCase(MessageRepository messageRepository) {
        return new EditMessageUseCase(messageRepository);
    }

    @Bean
    public DeleteMessageUseCase deleteMessageUseCase(MessageRepository messageRepository) {
        return new DeleteMessageUseCase(messageRepository);
    }

    @Bean
    public GetChannelHistoryUseCase getChannelHistoryUseCase(MessageRepository messageRepository) {
        return new GetChannelHistoryUseCase(messageRepository);
    }

    @Bean
    public SearchMessagesUseCase searchMessagesUseCase(MessageRepository messageRepository) {
        return new SearchMessagesUseCase(messageRepository);
    }

    @Bean
    public ListConversationsUseCase listConversationsUseCase(ChannelRepository channelRepository) {
        return new ListConversationsUseCase(channelRepository);
    }

    @Bean
    public GetMyProfileUseCase getMyProfileUseCase(UserRepository userRepository) {
        return new GetMyProfileUseCase(userRepository);
    }

    @Bean
    public UpdateMyProfileUseCase updateMyProfileUseCase(UserRepository userRepository) {
        return new UpdateMyProfileUseCase(userRepository);
    }

    @Bean
    public ListUsersUseCase listUsersUseCase(UserRepository userRepository) {
        return new ListUsersUseCase(userRepository);
    }

    @Bean
    public AskCopilotUseCase askCopilotUseCase(UserRepository userRepository, CopilotRepository copilotRepository,
                                                EmbeddingProvider embeddingProvider,
                                                ChatCompletionProvider chatCompletionProvider) {
        return new AskCopilotUseCase(userRepository, copilotRepository, embeddingProvider, chatCompletionProvider);
    }

    @Bean
    public ProcessEmbeddingJobsUseCase processEmbeddingJobsUseCase(EmbeddingJobRepository jobRepository,
                                                                     EmbeddingProvider embeddingProvider) {
        return new ProcessEmbeddingJobsUseCase(jobRepository, embeddingProvider);
    }

    @Bean
    public GetCopilotUsageUseCase getCopilotUsageUseCase(CopilotRepository copilotRepository) {
        return new GetCopilotUsageUseCase(copilotRepository);
    }
}
