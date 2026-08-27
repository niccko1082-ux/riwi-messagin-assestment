# Evidencias de ejecución

Capturas tomadas contra el stack real (`docker compose up --build` + carga del corpus
semilla vía `SeedLoader`, ver README raíz), navegador real (Playwright), sin datos
inventados — todo el contenido corresponde a `database/seed/seed.json`.

1. **`01-login.png`** — pantalla de inicio de sesión.
2. **`02-envio-mensaje.png`** — Camilo Zapata (Tech Lead) envía un mensaje en tiempo real
   en `#proyecto-fenix`; aparece de inmediato vía WebSocket y actualiza la vista previa de
   la conversación.
3. **`03-busqueda.png`** — búsqueda de "RLS" con el término resaltado (`ts_headline`).
4. **`04-copiloto-citas.png`** — Camilo (miembro de `#proyecto-fenix` **y**
   `#canal-directivo-confidencial`) pregunta por el sprint; el copiloto responde citando
   `[msg 1]` con enlace directo al mensaje fuente.
5. **`05-rls-lista-filtrada.png`** — Valentina Rios inicia sesión: su lista de
   conversaciones **no muestra** `#canal-directivo-confidencial` (no es miembro) — RLS
   filtrando en la capa de datos, no en el frontend.
6. **`06-copiloto-negativa.png`** — Valentina le pregunta al copiloto por el presupuesto
   confidencial (información que sí existe en la BD, en un canal ajeno a ella). El
   copiloto responde con honestidad ("no encontró contexto suficiente") sin inventar una
   respuesta; las citas que sí devuelve son todas de `#proyecto-fenix` — la recuperación
   nunca llegó a tocar los mensajes confidenciales, no es solo una negativa a nivel de
   prompt.

Reproducible con las credenciales del corpus semilla (`RiwiCoder#2026` para todos los
usuarios) — ver README raíz, sección "Cargar el corpus semilla".
