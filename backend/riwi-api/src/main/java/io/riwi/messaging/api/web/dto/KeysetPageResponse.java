package io.riwi.messaging.api.web.dto;

import io.riwi.messaging.domain.model.KeysetPage;

import java.util.List;
import java.util.function.Function;

public record KeysetPageResponse<T>(List<T> items, Long nextCursor) {
    public static <D, T> KeysetPageResponse<D> from(KeysetPage<T> page, Function<T, D> mapper) {
        return new KeysetPageResponse<>(page.items().stream().map(mapper).toList(), page.nextCursor());
    }
}
