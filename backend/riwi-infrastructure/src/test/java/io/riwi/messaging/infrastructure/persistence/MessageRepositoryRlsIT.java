package io.riwi.messaging.infrastructure.persistence;

import io.riwi.messaging.domain.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/** Fase 7 — contra PostgreSQL real (Testcontainers, imagen pgvector/pgvector para que
 *  CREATE EXTENSION vector de 001 funcione), aplicando las 10 migraciones reales vía psql
 *  (misma invocación que producción: :'app_password'/:'worker_password' son variables de
 *  psql, no placeholders JDBC). rw_app es el rol bajo prueba: sin RLS (008), un usuario
 *  ajeno al canal vería mensajes que no le corresponden.
 *
 *  ActorPropagation fija app.current_user_id con SET LOCAL (005): solo dura la transacción
 *  activa. Los repositorios asumen que Spring los invoca ya proxied por @Transactional; aquí,
 *  al llamarlos directamente, cada operación se envuelve a mano en su propia transacción
 *  (inTx) para que setActor y el statement que sigue compartan conexión. */
@Testcontainers
class MessageRepositoryRlsIT {

    private static final String APP_PASSWORD = "test_app_pw";
    private static final String WORKER_PASSWORD = "test_worker_pw";
    private static final String[] MIGRATIONS = {
            "001_create_extensions.sql", "002_create_tables.sql", "003_create_constraints.sql",
            "004_create_indexes.sql", "005_create_functions.sql", "006_create_triggers.sql",
            "007_create_views.sql", "008_create_rls.sql", "009_create_procedures.sql",
            "010_create_embedding_worker.sql"
    };

    // Campo estático + @Container: la extensión de JUnit 5 arranca el contenedor una sola vez
    // para toda la clase (equivalente a @BeforeAll) y lo detiene tras el último test
    // (equivalente a @AfterAll) — no requiere start()/stop() manual.
    @org.testcontainers.junit.jupiter.Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    private static JdbcTemplate appJdbc;
    private static TransactionTemplate appTx;
    private static UserId memberUser;
    private static UserId outsiderUser;
    private static ChannelId channelId;

    @BeforeAll
    static void migrateAndSeed() throws IOException, InterruptedException {
        applyMigrations();

        JdbcTemplate superuserJdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        DriverManagerDataSource appDataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), "rw_app", APP_PASSWORD);
        appJdbc = new JdbcTemplate(appDataSource);
        appTx = new TransactionTemplate(new DataSourceTransactionManager(appDataSource));

        memberUser = new UserId(seedUser(superuserJdbc, "miembro@riwi.co"));
        outsiderUser = new UserId(seedUser(superuserJdbc, "ajeno@riwi.co"));
        channelId = new ChannelId(seedChannel(superuserJdbc, memberUser.value()));
        superuserJdbc.update(
                "INSERT INTO rw_channel_members (channel_id, user_id) VALUES (?, ?)",
                channelId.value(), memberUser.value());
    }

    private static void applyMigrations() throws IOException, InterruptedException {
        // riwi.migrations.dir la fija maven-surefire-plugin desde project.basedir (pom.xml):
        // estable sin importar el cwd del runner. El fallback relativo cubre ejecutar la
        // clase directamente con java/IDE sin pasar por ese plugin.
        String configured = System.getProperty("riwi.migrations.dir");
        Path migrationsDir = configured != null ? Path.of(configured) : Path.of("../../database/migrations");
        for (String file : MIGRATIONS) {
            String containerPath = "/migrations/" + file;
            POSTGRES.copyFileToContainer(MountableFile.forHostPath(migrationsDir.resolve(file)), containerPath);
            Container.ExecResult result = POSTGRES.execInContainer("psql",
                    "-U", POSTGRES.getUsername(), "-d", POSTGRES.getDatabaseName(),
                    "-v", "ON_ERROR_STOP=1",
                    "-v", "app_password=" + APP_PASSWORD,
                    "-v", "worker_password=" + WORKER_PASSWORD,
                    "-f", containerPath);
            if (result.getExitCode() != 0) {
                throw new IllegalStateException("migración " + file + " falló:\n" + result.getStderr());
            }
        }
    }

    private static UUID seedUser(JdbcTemplate jdbc, String email) {
        return jdbc.queryForObject(
                "INSERT INTO rw_users (first_name, last_name, email, password_hash, job_title) " +
                        "VALUES ('Test', 'User', ?, 'x', 'QA') RETURNING user_id",
                UUID.class, email);
    }

    private static UUID seedChannel(JdbcTemplate jdbc, UUID createdBy) {
        return jdbc.queryForObject(
                "INSERT INTO rw_channels (name, channel_type, created_by) VALUES ('general', 'group', ?) RETURNING channel_id",
                UUID.class, createdBy);
    }

    private static <T> T inTx(Supplier<T> action) {
        return appTx.execute(status -> action.get());
    }

    @Test
    void outsiderCannotReadChannelHistory() {
        JdbcMessageRepository repo = new JdbcMessageRepository(appJdbc);
        inTx(() -> repo.send(memberUser, channelId, "hola equipo, esto es privado"));

        KeysetPage<Message> asOutsider = inTx(() -> repo.history(outsiderUser, channelId, null, 20));

        assertTrue(asOutsider.items().isEmpty(), "RLS debe ocultar mensajes de un canal ajeno");
    }

    @Test
    void memberReadsOwnChannelHistory() {
        JdbcMessageRepository repo = new JdbcMessageRepository(appJdbc);
        MessageId sent = inTx(() -> repo.send(memberUser, channelId, "mensaje visible para el miembro"));

        KeysetPage<Message> asMember = inTx(() -> repo.history(memberUser, channelId, null, 20));

        assertTrue(asMember.items().stream().anyMatch(m -> m.id().equals(sent)));
    }

    // Consulta 1: keyset sin OFFSET — cada página se ancla al último message_id visto.
    @Test
    void keysetPaginationWalksAllMessagesWithoutOffset() {
        JdbcMessageRepository repo = new JdbcMessageRepository(appJdbc);
        for (int i = 0; i < 5; i++) {
            int n = i;
            inTx(() -> repo.send(memberUser, channelId, "mensaje " + n));
        }

        KeysetPage<Message> firstPage = inTx(() -> repo.history(memberUser, channelId, null, 2));
        assertEquals(2, firstPage.items().size());
        assertNotNull(firstPage.nextCursor());

        KeysetPage<Message> secondPage = inTx(() -> repo.history(memberUser, channelId, firstPage.nextCursor(), 2));
        assertEquals(2, secondPage.items().size());

        long lastOfFirstPage = firstPage.items().get(firstPage.items().size() - 1).id().value();
        assertTrue(secondPage.items().stream().allMatch(m -> m.id().value() < lastOfFirstPage));
    }
}
