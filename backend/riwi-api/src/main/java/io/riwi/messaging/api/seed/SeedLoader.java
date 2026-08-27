package io.riwi.messaging.api.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.riwi.messaging.domain.port.PasswordHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Carga database/seed/seed.json (Entregable "scripts de carga" + Despliegue "comando
 *  documentado para... cargar el corpus completo"). Deshabilitado por defecto: solo corre
 *  con --riwi.seed.enabled=true (ver README), nunca en un arranque normal de la API.
 *  Idempotente por email/ref: reintentar sobre una base ya sembrada no duplica filas.
 *
 *  Usa su propio JdbcTemplate contra el superusuario, no el bean @Primary (rw_app, Fase 3):
 *  rw_app solo tiene SELECT sobre rw_users/rw_channels/rw_messages — todo INSERT pasa por
 *  funciones SECURITY DEFINER que no existen para "crear usuario/canal" (aprovisionamiento
 *  fuera del alcance de la API), igual que 'migrate' en docker-compose.yml. */
@Component
@ConditionalOnProperty(name = "riwi.seed.enabled", havingValue = "true")
public class SeedLoader implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(SeedLoader.class);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordHasher passwordHasher;
    private final ObjectMapper objectMapper;

    @Value("${riwi.seed.file}")
    private String seedFile;

    public SeedLoader(PasswordHasher passwordHasher, ObjectMapper objectMapper,
                       @Value("${riwi.seed.superuser-url}") String url,
                       @Value("${riwi.seed.superuser-username}") String username,
                       @Value("${riwi.seed.superuser-password}") String password) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.passwordHasher = passwordHasher;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        JsonNode root = objectMapper.readTree(new File(seedFile));
        Map<String, UUID> userIds = new HashMap<>();
        Map<String, UUID> channelIds = new HashMap<>();

        for (JsonNode u : root.get("users")) {
            String email = u.get("email").asText();
            UUID id = findUserByEmail(email);
            if (id == null) {
                id = UUID.randomUUID();
                jdbcTemplate.update(
                        "INSERT INTO rw_users (user_id, first_name, last_name, email, password_hash, job_title) " +
                                "VALUES (?, ?, ?, ?, ?, ?)",
                        id, u.get("first_name").asText(), u.get("last_name").asText(), email,
                        passwordHasher.hash(u.get("password").asText()), u.get("job_title").asText());
            }
            userIds.put(u.get("ref").asText(), id);
        }

        for (JsonNode c : root.get("channels")) {
            String ref = c.get("ref").asText();
            UUID createdBy = userIds.get(c.get("created_by").asText());
            UUID id = findChannelByCreatorAndName(createdBy, c.get("name"));
            if (id == null) {
                id = UUID.randomUUID();
                jdbcTemplate.update(
                        "INSERT INTO rw_channels (channel_id, name, channel_type, created_by) VALUES (?, ?, ?, ?)",
                        id, c.get("name").isNull() ? null : c.get("name").asText(),
                        c.get("channel_type").asText(), createdBy);
                for (JsonNode member : c.get("members")) {
                    jdbcTemplate.update(
                            "INSERT INTO rw_channel_members (channel_id, user_id) VALUES (?, ?)",
                            id, userIds.get(member.asText()));
                }
            }
            channelIds.put(ref, id);
        }

        int messagesInserted = 0;
        if (isMessagesTableEmpty()) {
            for (JsonNode m : root.get("messages")) {
                jdbcTemplate.update(
                        "INSERT INTO rw_messages (channel_id, sender_id, content) VALUES (?, ?, ?)",
                        channelIds.get(m.get("channel").asText()), userIds.get(m.get("sender").asText()),
                        m.get("content").asText());
                messagesInserted++;
            }
        }

        log.info("Corpus semilla cargado: {} usuarios, {} canales, {} mensajes",
                userIds.size(), channelIds.size(), messagesInserted);
        System.exit(0);
    }

    private UUID findUserByEmail(String email) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT user_id FROM rw_users WHERE email = ?", UUID.class, email);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    // query() + findFirst() en vez de queryForObject(): seed.json no tiene una columna que
    // ancle cada canal a su "ref" en BD, así que la idempotencia se aproxima por
    // (created_by, name) — con más de un canal sin nombre del mismo creador (dos DMs
    // distintos, por ejemplo) esto reutilizaría el primero en vez de fallar con
    // IncorrectResultSizeDataAccessException al reintentar la carga.
    private UUID findChannelByCreatorAndName(UUID createdBy, JsonNode name) {
        List<UUID> matches = name.isNull()
                ? jdbcTemplate.query(
                        "SELECT channel_id FROM rw_channels WHERE created_by = ? AND name IS NULL",
                        (rs, rowNum) -> (UUID) rs.getObject("channel_id"), createdBy)
                : jdbcTemplate.query(
                        "SELECT channel_id FROM rw_channels WHERE created_by = ? AND name = ?",
                        (rs, rowNum) -> (UUID) rs.getObject("channel_id"), createdBy, name.asText());
        return matches.isEmpty() ? null : matches.get(0);
    }

    private boolean isMessagesTableEmpty() {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM rw_messages", Long.class);
        return count != null && count == 0;
    }
}
