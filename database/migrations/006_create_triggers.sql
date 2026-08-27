-- Fase 3 — Triggers. Requisito explícito: al menos un trigger que mantenga el vector de
-- búsqueda consistente. Se implementan tres, cada uno con una responsabilidad distinta.

-- ── 1. Vector de búsqueda (tsvector) — síncrono, en BD ─────────────────────
-- Se recalcula en cada INSERT y en cada UPDATE que cambie el contenido. Usa el diccionario
-- 'spanish' (stemming: "encontró"/"encontrar" matchean) porque el corpus de la plataforma
-- es en español (ver database/seed/seed.json).
CREATE FUNCTION rw_messages_sync_search_vector()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.search_vector := to_tsvector('spanish', coalesce(NEW.content, ''));
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_rw_messages_search_vector
    BEFORE INSERT OR UPDATE OF content ON rw_messages
    FOR EACH ROW
    EXECUTE FUNCTION rw_messages_sync_search_vector();

-- Ahora que el trigger garantiza que todo INSERT/UPDATE de contenido puebla search_vector,
-- se puede exigir NOT NULL (la tabla está vacía en este punto de la migración).
ALTER TABLE rw_messages ALTER COLUMN search_vector SET NOT NULL;

-- ── 2. Outbox de embeddings — asíncrono, procesado por el backend ─────────
-- El embedding en sí requiere llamar a un proveedor de IA externo (NVIDIA NIM), así que este
-- trigger NO lo calcula: solo encola un job 'pending'. ON CONFLICT respeta el índice único
-- parcial de 004 (ux_rw_embedding_jobs_pending_per_message): si ya hay un job pendiente para
-- este mensaje, no se duplica.
CREATE FUNCTION rw_messages_enqueue_embedding_job()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO rw_embedding_jobs (message_id, status)
    VALUES (NEW.message_id, 'pending')
    ON CONFLICT (message_id) WHERE status = 'pending' DO NOTHING;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_rw_messages_embedding_job
    AFTER INSERT OR UPDATE OF content ON rw_messages
    FOR EACH ROW
    EXECUTE FUNCTION rw_messages_enqueue_embedding_job();

-- ── 3. updated_at de usuarios ───────────────────────────────────────────────
CREATE FUNCTION rw_touch_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_rw_users_touch_updated_at
    BEFORE UPDATE ON rw_users
    FOR EACH ROW
    EXECUTE FUNCTION rw_touch_updated_at();
