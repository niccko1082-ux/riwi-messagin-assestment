# DECISIONS.md

Registro de decisiones técnicas, justificaciones y recortes de alcance (MVP) tomados durante la prueba técnica "Assesment Empleabilidad - Cohorte 6" (Riwi Co.).

## Metodología

Se sigue un ciclo de vida de software basado en **Spec-Driven Development**: cada fase produce primero un artefacto de especificación (modelo de datos, contrato de API, contrato de proveedor de IA) que se valida antes de implementar contra él. Fases:

1. Análisis y modelado de datos (`docs/data-model.md`, `database/seed/seed.json`)
2. DDL PostgreSQL (`database/migrations/001-004`)
3. Lógica de negocio en BD: funciones, RLS, vistas, procedimientos, triggers (`database/migrations/005-009`, `database/queries/`)
4. Backend Clean Architecture (Spring Boot)
5. Copiloto de IA (RAG)
6. Frontend (React)
7. QA
8. Despliegue y documentación final

Cada fase se cierra con un checkpoint con el coder antes de avanzar a la siguiente, para garantizar que el código pueda explicarse en la sustentación técnica.

## Decisiones de stack (2026-08-27)

| Área | Decisión | Justificación |
|---|---|---|
| Backend | Java 21 + Spring Boot 3 | Elegido explícitamente por el coder. Permite Clean Architecture vía módulos separados (domain sin dependencias de Spring), Spring Security para JWT, Spring Data JDBC/JPA para invocar funciones y procedimientos de PostgreSQL. |
| Frontend | React + Vite + TypeScript | SPA responsiva rápida de levantar, ecosistema maduro para i18n (react-i18next), WebSocket client y manejo de estados de UI (pendiente/enviado/fallido). |
| Base de datos | PostgreSQL 15+ con extensión `pgvector` | Cumple el requisito de "base vectorial" sin infraestructura adicional: los embeddings de mensajes se guardan en la misma base relacional, simplificando transacciones y RLS sobre el contexto recuperado por el copiloto. |
| Proveedor de IA | NVIDIA NIM (build.nvidia.com), API compatible con el formato OpenAI | Permite cumplir "proveedor intercambiable vía interfaz específica tipo OpenAI SDK" sin costo: se define una interfaz Java `LlmProvider` / `EmbeddingProvider` (patrón Strategy/Adapter) y el adapter concreto apunta a NVIDIA NIM; cambiar a OpenAI real en el futuro solo implica un nuevo adapter, no tocar el dominio. |
| Tiempo real | WebSocket (Spring WebSocket / STOMP) | Requisito no negociable: "la mensajería debe funcionar en tiempo real". |
| Autenticación | JWT de acceso de vida corta + refresh token con rotación | Requisito explícito de la prueba; refresh token se almacena hasteado en `rw_refresh_tokens` con rotación en cada uso. |

## Recortes de alcance (MVP) — se irán agregando por fase

_(pendiente de completar conforme se ejecuta cada fase; todo recorte de alcance frente al PDF original se documentará aquí con su justificación)_
