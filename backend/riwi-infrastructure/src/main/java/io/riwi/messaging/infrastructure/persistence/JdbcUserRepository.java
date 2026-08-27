package io.riwi.messaging.infrastructure.persistence;

import io.riwi.messaging.domain.model.User;
import io.riwi.messaging.domain.model.UserId;
import io.riwi.messaging.domain.port.UserRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcUserRepository implements UserRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        try {
            return Optional.of(jdbcTemplate.queryForObject(
                    "SELECT * FROM rw_users WHERE lower(email) = lower(?)", this::mapUser, email));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<User> findById(UserId id) {
        try {
            return Optional.of(jdbcTemplate.queryForObject(
                    "SELECT * FROM rw_users WHERE user_id = ?", this::mapUser, id.value()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    // CALL rw_query_users(term, onlyActive, cursor) — procedimiento con OUT refcursor
    // (Fase 3). El driver de PostgreSQL entrega el refcursor como un ResultSet ya abierto al
    // registrarlo como Types.OTHER; solo es válido dentro de la transacción que lo abrió.
    @Override
    @Transactional(readOnly = true)
    public List<User> search(String term, boolean onlyActive) {
        try {
            return jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<List<User>>) conn -> {
                try (CallableStatement cs = conn.prepareCall("{call rw_query_users(?, ?, ?)}")) {
                    if (term == null) {
                        cs.setNull(1, Types.VARCHAR);
                    } else {
                        cs.setString(1, term);
                    }
                    cs.setBoolean(2, onlyActive);
                    cs.setNull(3, Types.OTHER);
                    cs.registerOutParameter(3, Types.OTHER);
                    cs.execute();
                    List<User> results = new ArrayList<>();
                    try (ResultSet rs = (ResultSet) cs.getObject(3)) {
                        while (rs.next()) {
                            results.add(mapUser(rs, 0));
                        }
                    }
                    return results;
                }
            });
        } catch (DataAccessException e) {
            throw PgErrorMapper.translate(e);
        }
    }

    // CALL rw_manage_user — autoservicio; el propio procedimiento valida actorId == targetId.
    @Override
    @Transactional
    public void manage(UserId actorId, UserId targetId, String firstName, String lastName,
                        String jobTitle, boolean deactivate) {
        try {
            ActorPropagation.setActor(jdbcTemplate, actorId);
            jdbcTemplate.update("{call rw_manage_user(?, ?, ?, ?, ?)}",
                    targetId.value(), firstName, lastName, jobTitle, deactivate);
        } catch (DataAccessException e) {
            throw PgErrorMapper.translate(e);
        }
    }

    private User mapUser(ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new User(
                new UserId(rs.getObject("user_id", UUID.class)),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("job_title"),
                rs.getBoolean("is_active"),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
