package io.riwi.messaging.infrastructure.persistence;

import io.riwi.messaging.domain.exception.ForbiddenException;
import io.riwi.messaging.domain.exception.NotFoundException;
import io.riwi.messaging.domain.exception.ValidationException;
import org.springframework.dao.DataAccessException;

import java.sql.SQLException;

/** Traduce SQLSTATEs personalizados de las funciones/procedimientos de la Fase 3
 *  (RAISE EXCEPTION ... USING ERRCODE = ...) a excepciones de dominio. Spring no conoce estos
 *  códigos (no son de su tabla estándar sql-error-codes), así que se leen directo del
 *  SQLException subyacente. Una excepción sin mapeo se relanza tal cual — el manejador
 *  global de la API la trata como error interno (500), nunca se filtra al cliente. */
public final class PgErrorMapper {
    private static final String NOT_FOUND = "P0002";
    private static final String INSUFFICIENT_PRIVILEGE = "42501";
    private static final String CHECK_VIOLATION = "23514";

    private PgErrorMapper() {
    }

    public static RuntimeException translate(DataAccessException ex) {
        SQLException sqlEx = ex.getRootCause() instanceof SQLException se ? se : null;
        if (sqlEx == null) {
            return ex;
        }
        String state = sqlEx.getSQLState();
        if (state == null) {
            return ex;
        }
        return switch (state) {
            case NOT_FOUND -> new NotFoundException(sqlEx.getMessage());
            case INSUFFICIENT_PRIVILEGE -> new ForbiddenException(sqlEx.getMessage());
            case CHECK_VIOLATION -> new ValidationException(sqlEx.getMessage());
            default -> ex;
        };
    }
}
