# Sustentación técnica

Mapa de cada requerimiento del PDF de la prueba ("Assesment Empleabilidad — Cohorte 6") a
dónde y cómo se cumple en este repositorio. Para el diseño y las decisiones detrás de cada
punto, ver `ARCHITECTURE.md` y `DECISIONS.md` — este documento es el índice de trazabilidad,
no los repite.

## 1. Análisis, normalización y modelo de datos

- **Modelo E-R** con entidades, PK, FK, cardinalidades y justificación del tipo de clave:
  `docs/data-model.md` §2-3, renderizado en [`docs/er-diagram.png`](docs/er-diagram.png).
- **Corpus `seed.json`** (entidades/relaciones/reglas de negocio implícitas, caso de prueba
  negativo de RLS documentado en su `_meta`): `database/seed/seed.json`.
- **Normalización 1FN/2FN/3FN** documentada: `docs/data-model.md` §4.

## 2. Implementación de base de datos en PostgreSQL

- PostgreSQL 15+, nombre `bd_nombre_apellido_clan`: `.env.example` / `docker-compose.yml`
  (servicio `db`, imagen `pgvector/pgvector:pg16`).
- Todas las tablas/columnas en inglés con prefijo `rw_`: `database/migrations/002_create_tables.sql`.
- DDL completo, PK, FK con `ON DELETE` explícito y justificado, `UNIQUE`, `NOT NULL`, `CHECK`,
  `timestamptz` en UTC: `database/migrations/002_create_tables.sql` (tablas),
  `003_create_constraints.sql` (FK/UNIQUE/CHECK, justificación de cada `ON DELETE` en
  `docs/data-model.md` §5).
- **Índice único parcial** (requisito explícito): `ux_rw_embedding_jobs_pending_per_message`
  en `database/migrations/004_create_indexes.sql` — evita encolar dos jobs de embedding en
  vuelo para el mismo mensaje.

## 3. Lógica de negocio en la base de datos

- **Funciones transaccionales** con validación de permisos en BD y sin rastros parciales
  ante error: `rw_send_message`, `rw_edit_message`, `rw_delete_message`
  (`database/migrations/005_create_functions.sql`) — todas `LANGUAGE plpgsql`, atómicas por
  ejecutarse dentro de la transacción que abre el JDBC del backend.
- **Row Level Security** sobre `rw_channels`/`rw_channel_members`/`rw_messages` (y las tablas
  dependientes), con `rw_app` (rol de aplicación sin `BYPASSRLS`) y el actor fijado por
  transacción vía `app.current_user_id`: `database/migrations/008_create_rls.sql`,
  propagación desde el backend en `ActorPropagation.java`
  (`backend/riwi-infrastructure/.../persistence/ActorPropagation.java`).
- **Vista de conversaciones del usuario**: `rw_user_conversations`
  (`database/migrations/007_create_views.sql`).
- **Dos procedimientos almacenados** (mínimo pedido): `rw_query_users` (consulta de usuarios,
  con `refcursor`) y `rw_manage_user` (edición/desactivación de usuarios, autoservicio)
  — `database/migrations/009_create_procedures.sql`.

## 4. Búsqueda, recuperación de contexto y seguridad

- El copiloto **nunca** ve mensajes globales, solo canales donde el actor es miembro: el
  filtro vive dentro de la función SQL `rw_copilot_search_context`
  (`database/migrations/005_create_functions.sql`, `SECURITY DEFINER`), documentada también
  en `database/queries/003_contexto_copiloto.sql`.
- **Base vectorial**: extensión `pgvector` sobre la misma PostgreSQL (`rw_message_embeddings`,
  columna `vector(1024)`), motor de embeddings NVIDIA NIM
  (`NvidiaEmbeddingAdapter.java`).
- **Trigger** que mantiene el `tsvector` de búsqueda consistente:
  `database/migrations/006_create_triggers.sql` (recalcula `search_vector` en cada
  `INSERT`/`UPDATE` de `content`).
- Prohibiciones respetadas: **sin borrado físico** (`is_deleted` + `deleted_at`, historial en
  `rw_message_revisions`), **sin SQL por concatenación** (`JdbcTemplate` parametrizado en
  toda la capa `riwi-infrastructure`, nunca `String` interpolado), **sin paginación por
  `OFFSET`** (keyset en `rw_messages.message_id`, ver Consulta 1).

## 5. Backend y API REST

- **Clean Architecture** con 4 módulos Maven (`riwi-domain` → `riwi-application` →
  `riwi-infrastructure` → `riwi-api`), dominio sin dependencias de Spring ni del driver de
  BD: detalle completo en `ARCHITECTURE.md`.
- **Casos de uso delgados** (`SendMessageUseCase`, `AskCopilotUseCase`, etc. en
  `backend/riwi-application/`): validan entrada, invocan un puerto, mapean el resultado.
- **SOLID y patrón de diseño** (Ports & Adapters / Dependency Inversion, justificado por el
  requisito "proveedor de IA intercambiable"): `ARCHITECTURE.md` sección "Patrón aplicado".
- **API REST**: códigos de estado correctos (`GlobalExceptionHandler.java`), identificador de
  correlación (`CorrelationIdFilter.java`, propagado en cada `ApiErrorResponse`), paginación
  por keyset (Consultas 1 y 2).

## 6. Autenticación y autorización

- Login verificando contraseña contra hash BCrypt: `BCryptPasswordHasherAdapter.java`,
  `LoginUseCase.java`.
- JWT de acceso de vida corta + refresh token con rotación, hasheado en BD (nunca en texto
  plano): `JwtTokenParser.java`, tabla `rw_refresh_tokens`
  (`database/migrations/002_create_tables.sql`).
- Rutas protegidas, `userId` tomado **exclusivamente** del token (nunca del cuerpo de la
  petición): `JwtAuthenticationFilter.java` → `CurrentActor`, usado en todos los
  `@RestController` vía `@AuthenticationPrincipal`.
- Actor propagado a las funciones de BD y a RLS: `ActorPropagation.setActor(...)` antes de
  cada operación en los repositorios JDBC.

## 7. Frontend

- Interfaz con **tres zonas** (conversación, copiloto, perfil): `AppPage.tsx` +
  `ChatPanel.tsx` / `CopilotPanel.tsx` / `ProfilePanel.tsx`.
- Envío de mensajes con estados **pendiente/enviado/fallido**: `ChatPanel.tsx`
  (`PendingSend`, `attemptSend`, `retrySend`) — optimista en el cliente, nunca persiste en
  `rw_messages` si falla (ver `docs/data-model.md` §1).
- Carga de historial diferida (keyset, botón "Cargar mensajes anteriores"), preservando la
  posición de scroll al anteponer mensajes antiguos (`useLayoutEffect` con anclaje por
  `scrollHeight` en `ChatPanel.tsx`), y estados de **carga**, **vacío** y **error** (inline,
  con reintento) sobre ese mismo historial.
- Responsiva en móvil y escritorio (`index.css`, breakpoint 900px, `.mobile-tabs`),
  disponible en **español e inglés** (`react-i18next`, `src/i18n/index.ts`), sin cadenas
  incrustadas en componentes (todo el texto vía `t(...)`).

## 8. Copiloto de IA

- RAG restringido al actor: `AskCopilotUseCase.java` usa `rw_copilot_search_context` con el
  `UserId` del token — nunca un canal fuera del alcance del actor.
- Cada respuesta incluye **citas** a los mensajes fuente (`Citation`, chips `msg·N` en
  `CopilotPanel.tsx`) y responde con **honestidad** cuando el contexto es insuficiente
  (`hadSufficientContext`, umbral `MIN_SIMILARITY` calibrado — ver `DECISIONS.md`).
- El copiloto conoce nombre y cargo del actor, construidos en el servidor desde el token
  (nunca del cliente): `AskCopilotUseCase.buildPrompt()`, `actor.fullName()`/`jobTitle()`.
- **Proveedor de IA intercambiable**: puertos de dominio `EmbeddingProvider`/
  `ChatCompletionProvider` (`riwi-domain/port/`), implementados por adaptadores NVIDIA NIM
  (API compatible con el formato OpenAI) en `riwi-infrastructure/ai/` — cambiar de proveedor
  implica un adaptador nuevo, no tocar dominio ni aplicación.
- **System prompt versionado**: constante `SYSTEM_PROMPT_VERSION = "copilot-v1"`
  (`AskCopilotUseCase.java`), persistida en cada fila de `rw_copilot_queries`.
- El contenido de los mensajes citados se trata explícitamente como **dato, no instrucción**
  (comentario y estructura del prompt en `buildPrompt()`, delimitado con `<contexto>`), y hay
  negativas explícitas por falta de permisos, fuera de alcance o contexto insuficiente
  (mismo método).

## 9. QA, evidencias y extras

- **Dos o más pruebas automatizadas contra PostgreSQL real** (Testcontainers, no mocks):
  `backend/riwi-infrastructure/src/test/java/.../MessageRepositoryRlsIT.java` —
  `outsiderCannotReadChannelHistory` (rechaza a un no-miembro), `memberReadsOwnChannelHistory`
  y `keysetPaginationWalksAllMessagesWithoutOffset` (confirma que no se retornan mensajes de
  canales privados ajenos, y que el keyset funciona sin `OFFSET`).
- **Evidencias de ejecución**: [`docs/evidencias/`](docs/evidencias/) — login, mensaje en
  tiempo real, búsqueda resaltada, copiloto con citas, y la negativa honesta del copiloto a
  un usuario sin acceso a un canal confidencial (con las citas devueltas probando que la
  recuperación misma nunca tocó ese canal, no solo el prompt).

## 10. Despliegue

- `docker compose up --build` levanta base de datos, backend y frontend:
  `docker-compose.yml` (servicios `db`, `migrate`, `backend`, `frontend`).
- Comando documentado para migraciones y carga del corpus completo: `README.md` §"Arranque
  rápido" y §"Cargar el corpus semilla" (`SeedLoader.java`, idempotente, hashea contraseñas
  con BCrypt al cargar).
- `.env.example` sin secretos reales; proyecto verificado levantando en una máquina limpia
  siguiendo solo el `README.md` (validado en esta sesión con `docker compose down -v` seguido
  de `up --build` + carga del corpus, de punta a punta).

## 11. Consultas y funciones SQL requeridas

Como archivos independientes, extraídos del código real y validados contra Postgres con RLS
activo — ver `database/queries/`:

- **Consulta 1** — historial por keyset: `database/queries/001_historial_keyset.sql`.
- **Consulta 2** — búsqueda con resaltado: `database/queries/002_busqueda_resaltada.sql`.
- **Consulta 3** — contexto del copiloto con permisos en SQL:
  `database/queries/003_contexto_copiloto.sql`.
- **Consulta 4** — consumo acumulado del copiloto: `database/queries/004_uso_copiloto.sql`.

## Condiciones de invalidación (verificadas)

- **Contraseñas nunca en texto plano**: BCrypt en `rw_users.password_hash`
  (`BCryptPasswordHasherAdapter`) y en `SeedLoader` al cargar el corpus.
