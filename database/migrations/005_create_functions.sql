-- Fase 3 — Lógica de negocio en PostgreSQL. Funciones transaccionales: cada una valida
-- permisos dentro de la propia base de datos (no confía en que el backend ya lo hizo) y,
-- cuando toca más de una tabla, corre atómica: si algo falla a mitad de camino, PostgreSQL
-- revierte todo — no quedan rastros parciales.
--
-- Todas usan SECURITY DEFINER + `SET search_path = public` fijo: corren con los privilegios
-- de quien las creó (no con los del rol de aplicación de bajo privilegio), pero fijar el
-- search_path evita el ataque clásico de "search_path hijacking" contra funciones
-- SECURITY DEFINER. rw_app nunca recibe INSERT/UPDATE/DELETE directo sobre las tablas base
-- (ver 008_create_rls.sql) — todo cambio pasa por una de estas funciones.

-- ── Actor de la transacción ────────────────────────────────────────────────
-- El backend fija el actor autenticado (extraído del JWT, nunca del cuerpo de la petición)
-- una vez por transacción con rw_set_current_user(); todo lo demás lo lee con
-- rw_current_user_id(). set_config() recibe el valor como parámetro normal de función
-- (no como texto interpolado en un SET), así que no hay riesgo de inyección aquí.

CREATE FUNCTION rw_set_current_user(p_user_id uuid)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM set_config('app.current_user_id', p_user_id::text, true); -- true = SET LOCAL (dura solo la transacción)
END;
$$;

CREATE FUNCTION rw_current_user_id()
RETURNS uuid
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    v_raw text := current_setting('app.current_user_id', true);
BEGIN
    IF v_raw IS NULL OR v_raw = '' THEN
        RAISE EXCEPTION 'app.current_user_id no está definido para esta transacción'
            USING ERRCODE = '28000'; -- invalid_authorization_specification
    END IF;
    RETURN v_raw::uuid;
END;
$$;

-- Helper para las políticas RLS de 008. SECURITY DEFINER + propiedad del owner de las
-- migraciones (que no tiene FORCE ROW LEVEL SECURITY activado) hace que esta lectura de
-- rw_channel_members se ejecute SIN aplicar RLS. Es necesario: si la política de
-- rw_channel_members subconsultara la propia rw_channel_members, PostgreSQL detecta
-- recursión infinita (la subconsulta necesitaría evaluar la misma política para decidir
-- qué filas son visibles, indefinidamente). Este es el patrón estándar para RLS
-- "eres visible si compartes una fila con el actor actual" sobre la misma tabla.
CREATE FUNCTION rw_my_channel_ids()
RETURNS SETOF uuid
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT channel_id FROM rw_channel_members WHERE user_id = rw_current_user_id();
$$;

-- ── Mensajería ──────────────────────────────────────────────────────────────

-- El remitente SIEMPRE es el actor de la transacción, nunca un parámetro: aunque el backend
-- ya validó el JWT, esta es la última línea de defensa contra un sender_id falsificado.
CREATE FUNCTION rw_send_message(p_channel_id uuid, p_content text)
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id    uuid := rw_current_user_id();
    v_message_id bigint;
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM rw_channel_members
         WHERE channel_id = p_channel_id AND user_id = v_user_id
    ) THEN
        RAISE EXCEPTION 'el usuario % no es miembro del canal %', v_user_id, p_channel_id
            USING ERRCODE = '42501'; -- insufficient_privilege
    END IF;

    IF btrim(coalesce(p_content, '')) = '' THEN
        RAISE EXCEPTION 'el contenido del mensaje no puede estar vacío'
            USING ERRCODE = '23514'; -- check_violation
    END IF;

    INSERT INTO rw_messages (channel_id, sender_id, content)
    VALUES (p_channel_id, v_user_id, p_content)
    RETURNING message_id INTO v_message_id;

    RETURN v_message_id;
END;
$$;

-- Edita un mensaje propio. Archiva el contenido anterior en rw_message_revisions ANTES de
-- sobrescribirlo — INSERT + UPDATE en la misma transacción implícita de la función: si el
-- UPDATE fallara, el INSERT también se revierte (todo o nada).
CREATE FUNCTION rw_edit_message(p_message_id bigint, p_new_content text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id     uuid := rw_current_user_id();
    v_sender_id   uuid;
    v_old_content text;
    v_is_deleted  boolean;
BEGIN
    SELECT sender_id, content, is_deleted
      INTO v_sender_id, v_old_content, v_is_deleted
      FROM rw_messages
     WHERE message_id = p_message_id
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'el mensaje % no existe', p_message_id USING ERRCODE = 'P0002';
    END IF;

    IF v_sender_id <> v_user_id THEN
        RAISE EXCEPTION 'el usuario % no puede editar un mensaje que no es suyo', v_user_id
            USING ERRCODE = '42501';
    END IF;

    IF v_is_deleted THEN
        RAISE EXCEPTION 'no se puede editar un mensaje eliminado' USING ERRCODE = '22023';
    END IF;

    IF btrim(coalesce(p_new_content, '')) = '' THEN
        RAISE EXCEPTION 'el contenido del mensaje no puede estar vacío' USING ERRCODE = '23514';
    END IF;

    INSERT INTO rw_message_revisions (message_id, previous_content, revision_type, edited_by)
    VALUES (p_message_id, v_old_content, 'edit', v_user_id);

    UPDATE rw_messages
       SET content = p_new_content,
           edited_at = now()
     WHERE message_id = p_message_id;
END;
$$;

-- Elimina (lógicamente) un mensaje propio. Nunca hace DELETE físico — marca is_deleted y
-- preserva el último contenido en el historial de revisiones, igual que en la edición.
CREATE FUNCTION rw_delete_message(p_message_id bigint)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id     uuid := rw_current_user_id();
    v_sender_id   uuid;
    v_old_content text;
    v_is_deleted  boolean;
BEGIN
    SELECT sender_id, content, is_deleted
      INTO v_sender_id, v_old_content, v_is_deleted
      FROM rw_messages
     WHERE message_id = p_message_id
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'el mensaje % no existe', p_message_id USING ERRCODE = 'P0002';
    END IF;

    IF v_sender_id <> v_user_id THEN
        RAISE EXCEPTION 'el usuario % no puede eliminar un mensaje que no es suyo', v_user_id
            USING ERRCODE = '42501';
    END IF;

    IF v_is_deleted THEN
        RETURN; -- idempotente: ya estaba eliminado, no es un error
    END IF;

    INSERT INTO rw_message_revisions (message_id, previous_content, revision_type, edited_by)
    VALUES (p_message_id, v_old_content, 'delete', v_user_id);

    -- content se redacta: sin esto, cualquier miembro del canal seguiría pudiendo leer el
    -- texto original vía SELECT directo (RLS solo filtra por canal, no por is_deleted). El
    -- contenido real ya quedó preservado arriba en rw_message_revisions.
    UPDATE rw_messages
       SET is_deleted = TRUE,
           deleted_at = now(),
           content = '[mensaje eliminado]'
     WHERE message_id = p_message_id;
END;
$$;

-- ── Copiloto RAG ────────────────────────────────────────────────────────────

-- Consulta 3 (requisito 11): recuperación de contexto para el copiloto con permisos en SQL.
-- El filtro de membresía vive DENTRO de la función (no solo en RLS): sigue siendo correcta
-- aunque en el futuro alguien la llame desde un rol distinto a rw_app.
CREATE FUNCTION rw_copilot_search_context(p_query_embedding vector(1024), p_match_count integer DEFAULT 8)
RETURNS TABLE (message_id bigint, channel_id uuid, content text, similarity numeric)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT m.message_id,
           m.channel_id,
           m.content,
           (1 - (e.embedding <=> p_query_embedding))::numeric AS similarity
      FROM rw_message_embeddings e
      JOIN rw_messages m ON m.message_id = e.message_id
     WHERE m.is_deleted = FALSE
       AND m.channel_id IN (SELECT rw_my_channel_ids())
     ORDER BY e.embedding <=> p_query_embedding
     LIMIT p_match_count;
$$;

-- Registra una consulta al copiloto y sus citas (para Consulta 4: consumo acumulado por
-- usuario). Las citas a mensajes fuera del alcance del actor se descartan en silencio en vez
-- de romper toda la transacción — es una salvaguarda adicional, no la única (la recuperación
-- ya viene filtrada por rw_copilot_search_context).
CREATE FUNCTION rw_log_copilot_query(
    p_question               text,
    p_answer                 text,
    p_tokens_used             integer,
    p_had_sufficient_context  boolean,
    p_system_prompt_version   text,
    p_cited_message_ids       bigint[] DEFAULT NULL,
    p_similarity_scores       numeric[] DEFAULT NULL
)
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id  uuid := rw_current_user_id();
    v_query_id bigint;
    v_index    integer;
BEGIN
    INSERT INTO rw_copilot_queries
        (user_id, question, answer, tokens_used, had_sufficient_context, system_prompt_version)
    VALUES
        (v_user_id, p_question, p_answer, p_tokens_used, p_had_sufficient_context, p_system_prompt_version)
    RETURNING query_id INTO v_query_id;

    -- coalesce(..., 1, 0): array_length de un array vacío ('{}') es NULL, no 0, y un FOR con
    -- límite superior NULL revienta con "upper bound of FOR loop cannot be null". El array
    -- vacío es un caso normal (respuesta del copiloto sin citas), no una excepción.
    IF p_cited_message_ids IS NOT NULL THEN
        FOR v_index IN 1 .. coalesce(array_length(p_cited_message_ids, 1), 0) LOOP
            INSERT INTO rw_copilot_citations (query_id, message_id, similarity_score)
            SELECT v_query_id, p_cited_message_ids[v_index], p_similarity_scores[v_index]
             WHERE EXISTS (
                     SELECT 1 FROM rw_messages m
                      WHERE m.message_id = p_cited_message_ids[v_index]
                        AND m.channel_id IN (SELECT rw_my_channel_ids())
                   )
            ON CONFLICT DO NOTHING;
        END LOOP;
    END IF;

    RETURN v_query_id;
END;
$$;

-- ── Worker de embeddings (outbox) ───────────────────────────────────────────
-- Llamadas por el backend después de recibir el vector del proveedor de IA para un job
-- encolado por el trigger de 006. No usan rw_current_user_id(): son un proceso interno, no
-- una acción de un usuario autenticado.

CREATE FUNCTION rw_record_embedding(p_job_id bigint, p_embedding vector(1024), p_embedding_model text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_message_id bigint;
BEGIN
    SELECT message_id INTO v_message_id FROM rw_embedding_jobs WHERE job_id = p_job_id FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'el job de embedding % no existe', p_job_id USING ERRCODE = 'P0002';
    END IF;

    INSERT INTO rw_message_embeddings (message_id, embedding, embedding_model)
    VALUES (v_message_id, p_embedding, p_embedding_model)
    ON CONFLICT (message_id) DO UPDATE
        SET embedding = EXCLUDED.embedding,
            embedding_model = EXCLUDED.embedding_model,
            updated_at = now();

    UPDATE rw_embedding_jobs
       SET status = 'done', processed_at = now()
     WHERE job_id = p_job_id;
END;
$$;

CREATE FUNCTION rw_fail_embedding_job(p_job_id bigint)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    UPDATE rw_embedding_jobs SET status = 'failed', processed_at = now() WHERE job_id = p_job_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'el job de embedding % no existe', p_job_id USING ERRCODE = 'P0002';
    END IF;
END;
$$;
