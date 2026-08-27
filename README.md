# Riwi Messaging

Plataforma interna de mensajería para Riwi Co. S.A.S.: chat en tiempo real por canales,
búsqueda full-text, historial paginado por keyset y un copiloto de IA (RAG) que responde
solo con base en mensajes a los que el usuario tiene acceso.

- **Backend**: Java 21 + Spring Boot, Clean Architecture (`domain` → `application` →
  `infrastructure` → `api`).
- **Base de datos**: PostgreSQL 15+ con `pgvector`, Row Level Security por canal/membresía.
- **Frontend**: React + Vite + TypeScript, WebSocket (STOMP) para tiempo real.
- **IA**: NVIDIA NIM (API compatible con OpenAI) para embeddings y chat completion.

Ver `docs/data-model.md` y `ARCHITECTURE.md` para el diseño detallado, y `DECISIONS.md` para
las decisiones técnicas registradas durante el desarrollo.

## Arranque rápido (Docker Compose)

Requiere Docker y Docker Compose.

```bash
cp .env.example .env
# Editar .env: como mínimo, NVIDIA_API_KEY (https://build.nvidia.com) y JWT_SECRET.
docker compose up --build
```

Servicios:

| Servicio   | URL                          |
|------------|-------------------------------|
| Frontend   | http://localhost:5173         |
| Backend    | http://localhost:8080          |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| PostgreSQL | localhost:5432                 |

`docker compose up` levanta Postgres, aplica las 10 migraciones (`database/migrations/`,
servicio `migrate`, se ejecuta una sola vez) y arranca backend y frontend. Los usuarios se
provisionan directamente en base de datos (no hay registro público) — ver
`database/seed/seed.json` para datos de ejemplo, o insertar filas en `rw_users`.

Para detener y limpiar (incluye el volumen de datos de Postgres):

```bash
docker compose down -v
```

## Desarrollo local (sin Docker)

### Base de datos

```bash
# Postgres 15+ con la extensión pgvector disponible, ya corriendo.
psql -U postgres -c "CREATE DATABASE bd_nombre_apellido_clan"
for f in database/migrations/*.sql; do
  psql -U postgres -d bd_nombre_apellido_clan \
       -v app_password="$DB_APP_PASSWORD" -v worker_password="$DB_WORKER_PASSWORD" \
       -f "$f"
done
```

### Backend

Spring Boot lee variables de entorno del proceso, no un archivo `.env` — hay que exportarlas
antes de arrancar (`DB_HOST` debe ser `localhost`, a diferencia del `db` de Docker Compose):

```bash
cd backend
export $(grep -v '^#' ../.env.example | xargs) DB_HOST=localhost
./mvnw spring-boot:run -pl riwi-api -am
```

Pruebas de integración contra PostgreSQL real (Testcontainers, requiere Docker):

```bash
./mvnw test
```

### Frontend

```bash
cd frontend
npm install
npm run dev   # http://localhost:5173, usa frontend/.env.development
```

## Variables de entorno

Ver `.env.example` — nunca commitear `.env` con secretos reales (ya está en
`.gitignore`). `NVIDIA_API_KEY` se obtiene en https://build.nvidia.com; los modelos por
defecto (`NVIDIA_CHAT_MODEL`/`NVIDIA_EMBEDDING_MODEL`) fueron verificados contra
`/v1/models` — NVIDIA retira modelos con frecuencia, revisar si alguno responde 404/410.
