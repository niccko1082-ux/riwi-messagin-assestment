package io.riwi.messaging.infrastructure.persistence;

import io.riwi.messaging.domain.model.UserId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

/** Fija app.current_user_id (Fase 3) para la transacción actual. Debe llamarse dentro de un
 *  método @Transactional: JdbcTemplate liga cada statement a la conexión de la transacción
 *  activa (Spring lo resuelve por hilo), así que esta llamada y las siguientes en el mismo
 *  método comparten conexión — condición necesaria para que RLS/rw_current_user_id() vean
 *  el mismo actor. */
public final class ActorPropagation {
    private ActorPropagation() {
    }

    public static void setActor(JdbcTemplate jdbcTemplate, UserId actorId) {
        // rw_set_current_user es una FUNCTION invocada con SELECT (retorna una fila), no un
        // DML — jdbcTemplate.update() llama a Statement.executeUpdate(), que el driver de
        // PostgreSQL rechaza cuando la sentencia sí devuelve resultados. query() con un
        // ResultSetExtractor que descarta el valor ejecuta correctamente sin ese conflicto.
        jdbcTemplate.query("SELECT rw_set_current_user(?::uuid)",
                (ResultSetExtractor<Void>) rs -> null, actorId.value());
    }
}
