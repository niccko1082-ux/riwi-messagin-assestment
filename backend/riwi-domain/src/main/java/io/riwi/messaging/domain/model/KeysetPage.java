package io.riwi.messaging.domain.model;

import java.util.List;

/** Página de resultados por keyset: sin OFFSET (prohibido por la prueba). nextCursor es null
 *  cuando no hay más resultados. */
public record KeysetPage<T>(List<T> items, Long nextCursor) {
}
