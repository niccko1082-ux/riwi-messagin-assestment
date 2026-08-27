-- Fase 3 — Procedimientos almacenados (requisito explícito: mínimo dos, invocados con CALL,
-- no SELECT — se diferencian a propósito de las funciones de 005).

-- ── 1. Consulta de usuarios ─────────────────────────────────────────────────
-- Devuelve resultados vía refcursor (los PROCEDURE de PostgreSQL no pueden hacer RETURNS
-- TABLE como las funciones). El '%' se concatena sobre el VALOR del parámetro para armar un
-- patrón ILIKE — se sigue pasando como parámetro ligado, nunca se concatena SQL ejecutable,
-- así que esto no es el "SQL por concatenación" que prohíbe la prueba. Sin OFFSET.
CREATE PROCEDURE rw_query_users(
    IN    p_search      text DEFAULT NULL,
    IN    p_only_active  boolean DEFAULT TRUE,
    INOUT p_cursor        refcursor DEFAULT 'rw_query_users_cursor'
)
LANGUAGE plpgsql
AS $$
BEGIN
    OPEN p_cursor FOR
        SELECT user_id, first_name, last_name, email, job_title, is_active, created_at
          FROM rw_users
         WHERE (p_only_active IS FALSE OR is_active = TRUE)
           AND (
                 p_search IS NULL
              OR first_name ILIKE '%' || p_search || '%'
              OR last_name  ILIKE '%' || p_search || '%'
              OR email      ILIKE '%' || p_search || '%'
               )
         ORDER BY last_name, first_name;
END;
$$;

-- Uso típico (dentro de una transacción, el cursor solo vive mientras esta dure):
--   BEGIN;
--   CALL rw_query_users('cami', TRUE, 'cur1');
--   FETCH ALL FROM cur1;
--   COMMIT;

-- ── 2. Edición y eliminación de usuarios ────────────────────────────────────
-- Un único procedimiento para ambas operaciones (así lo pide la prueba: "un procedimiento
-- de para la edición y eliminación de usuarios"). La "eliminación" es siempre lógica
-- (is_active = false): igual que los mensajes, un usuario nunca se borra físicamente —
-- rw_messages.sender_id depende de que la fila siga existiendo (ON DELETE RESTRICT,
-- docs/data-model.md §5). p_deactivate=true ignora los demás campos de edición.
--
-- Autoservicio únicamente: el actor solo puede editar/desactivar SU PROPIA cuenta
-- (p_user_id debe ser rw_current_user_id()). No existe rol de administrador en el modelo de
-- datos (Fase 1); un procedimiento separado con validación de rol quedaría fuera del
-- alcance de esta prueba. Hallazgo de seguridad corregido en la Fase 3: la primera versión
-- de este procedimiento no validaba el actor, permitiendo que cualquier usuario autenticado
-- desactivara o renombrara la cuenta de cualquier otro (IDOR / escalación de privilegios).
CREATE PROCEDURE rw_manage_user(
    IN p_user_id     uuid,
    IN p_first_name  text DEFAULT NULL,
    IN p_last_name   text DEFAULT NULL,
    IN p_job_title   text DEFAULT NULL,
    IN p_deactivate  boolean DEFAULT FALSE
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM rw_users WHERE user_id = p_user_id) THEN
        RAISE EXCEPTION 'el usuario % no existe', p_user_id USING ERRCODE = 'P0002';
    END IF;

    IF p_user_id <> rw_current_user_id() THEN
        RAISE EXCEPTION 'el usuario % no puede modificar la cuenta de %', rw_current_user_id(), p_user_id
            USING ERRCODE = '42501';
    END IF;

    UPDATE rw_users
       SET first_name = coalesce(p_first_name, first_name),
           last_name  = coalesce(p_last_name, last_name),
           job_title  = coalesce(p_job_title, job_title),
           is_active  = CASE WHEN p_deactivate THEN FALSE ELSE is_active END
     WHERE user_id = p_user_id;
    -- updated_at se actualiza solo, vía trg_rw_users_touch_updated_at (006).
END;
$$;

-- Mismo default-deny que 008 (PostgreSQL también otorga EXECUTE a PUBLIC en procedimientos
-- nuevos): estos dos procedimientos se crearon después del REVOKE FROM PUBLIC de 008, así
-- que necesitan el suyo propio antes de la allow-list explícita de abajo.
REVOKE EXECUTE ON PROCEDURE rw_query_users(text, boolean, refcursor) FROM PUBLIC;
REVOKE EXECUTE ON PROCEDURE rw_manage_user(uuid, text, text, text, boolean) FROM PUBLIC;

GRANT EXECUTE ON PROCEDURE rw_query_users(text, boolean, refcursor) TO rw_app;
GRANT EXECUTE ON PROCEDURE rw_manage_user(uuid, text, text, text, boolean) TO rw_app;
