# ARCHITECTURE.md

## Backend — Clean Architecture (Java 21 + Spring Boot)

4 módulos Maven, dependencias apuntando siempre hacia el dominio (verificable con
`mvn dependency:tree`, o a simple vista: `riwi-domain/pom.xml` no declara ninguna
dependencia de Spring ni del driver de PostgreSQL):

```
riwi-domain          -> sin dependencias externas
riwi-application      -> depende solo de riwi-domain
riwi-infrastructure    -> implementa los puertos de riwi-domain (JDBC, JWT, BCrypt)
riwi-api              -> composition root (Spring Boot): controllers, WebSocket, wiring
```

- **domain**: entidades (`User`, `Message`, `ConversationSummary`...), value objects
  (`UserId`, `ChannelId`, `MessageId`), excepciones selladas (`NotFoundException`,
  `ForbiddenException`, `ValidationException`) y **puertos** (`UserRepository`,
  `MessageRepository`, `PasswordHasher`, `AccessTokenIssuer`...).
- **application**: casos de uso delgados (`SendMessageUseCase`, `LoginUseCase`...) — POJOs
  sin anotaciones de Spring, testeables sin contexto de aplicación. Validan entrada,
  invocan un puerto, mapean el resultado.
- **infrastructure**: adaptadores JDBC que llaman a las funciones/procedimientos de
  PostgreSQL (Fase 3), y adaptadores de seguridad (BCrypt, JWT vía jjwt).
- **api**: controllers REST, filtro JWT, manejo global de errores, WebSocket (STOMP), y el
  `@Configuration` que instancia los casos de uso con sus adaptadores concretos (única capa
  que conoce todas las demás).

### Patrón aplicado: Ports & Adapters (Hexagonal) + Dependency Inversion

El dominio define interfaces (`UserRepository`, `PasswordHasher`, `TokenHasher`,
`AccessTokenIssuer`); `infrastructure` las implementa. `application` depende de las
interfaces, nunca de las implementaciones — se justifica porque el requisito explícito de
la prueba ("el proveedor de IA debe ser intercambiable", "el dominio no debe depender del
framework web ni del driver de base de datos") es exactamente el problema que este patrón
resuelve: cambiar de PostgreSQL/JDBC a otro store, o de BCrypt a otro hash, no toca
`application` ni `domain`.

### SOLID demostrable

- **SRP**: cada caso de uso hace una sola cosa (`SendMessageUseCase` no valida JWT ni
  mapea HTTP).
- **OCP**: nuevos proveedores (IA, hashing) se agregan implementando el puerto, sin tocar
  casos de uso existentes.
- **LSP**: cualquier implementación de `MessageRepository` es intercambiable sin romper
  `SendMessageUseCase`.
- **ISP**: puertos pequeños y específicos (`PasswordHasher` separado de `TokenHasher`,
  aunque ambos "hashean") en vez de una interfaz `SecurityService` genérica.
- **DIP**: `application`/`domain` dependen de abstracciones; `infrastructure` depende de
  ellas e implementa. El flujo de dependencias siempre apunta hacia adentro.

### Seguridad y propagación del actor

El actor autenticado se extrae **solo** del JWT (`JwtAuthenticationFilter`), nunca del
cuerpo de la petición. Cada adaptador JDBC que necesita RLS/permisos de BD llama primero
`SELECT rw_set_current_user(?)` y luego la función real, dentro de la misma transacción
(`ActorPropagation`, ligado a la conexión de Spring vía `@Transactional`) — así
`rw_current_user_id()` en PostgreSQL siempre ve al mismo actor que autenticó la petición
HTTP.

### Manejo de errores

Las funciones de PostgreSQL (Fase 3) señalan errores con `SQLSTATE` personalizados
(`P0002` no encontrado, `42501` sin permiso, `23514` violación de regla). `PgErrorMapper`
los traduce a excepciones de dominio; `GlobalExceptionHandler` las traduce a códigos HTTP
correctos con un cuerpo uniforme que incluye el `X-Correlation-Id` de la petición.
