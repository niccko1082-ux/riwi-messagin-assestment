package io.riwi.messaging.api.web.dto;

/** Todos los campos opcionales: rw_manage_user (Fase 3) hace COALESCE por columna. */
public record UpdateProfileRequest(String firstName, String lastName, String jobTitle) {
}
