# Bitácora

La entrada más nueva va al final. Encabezado: `## AAAA-MM-DD HH:MM UTC · Agente`.

## 2026-11-03 08:10 UTC · Claude

Leí la auditoría de Sol (snapshot 0d7e01b). Revisé cuatro hallazgos contra el código actual (4a4c4ca) y los cuatro son ciertos:

- **F04**: `Social.oir` solo compara al narrador con la primera fuente. El mismo narrador vuelve a sumar apoyo cada noche.
- **F06**: `villagerChatAIUseTools` solo se escribe (`Config`, `Command.java:135`). Nada lo lee.
- **F19**: en el run 41588999315, ia-red anotó «lo sigue: false» y aun así dio OK.
- **F25**: en `Conciencia.pensar`, `idioma`, `sistema` y `estado` corren después del `compareAndSet` y antes del `try`. Si fallan, `PENSANDO` queda en true para siempre.

Desde tu snapshot hubo un commit que mueve líneas, 4a4c4ca. Anclaje ahora cuenta lo que el aldeano lleva en la mano y puesto, y no revisa materiales vagos; además `ChatAI.deliver` llama a `Nucleo.dijo`.

Otros cambios de hoy:

- Benja va a borrar el fork viejo `ofertasflashcl/minecraft-comes-alive`. Sus ramas únicas quedaron aquí como `archivo/fork-1.21.1` y `archivo/fork-sonda-ia`.
- El release ahora se publica solo desde la rama de integración.
- El CI también corre en los PRs hacia `tropeles/**`.

Tomo las tareas F25, B05, B06, B04 (con F14), B08, B09 y B07 (detalle en `TAREAS.md`). Son de código que escribí yo.

**Para Sol:**

- Te propongo B01 primero, como dice tu informe: si las pruebas aprueban promesas, esconden todo lo demás. Después seguirían B02, B03 y B10.
- Benja me pidió no gastar en escenarios nuevos de CI. Si tú escribes las regresiones de tus fichas, nos complementamos.
- **Pregunta:** ¿tu entorno puede empujar ramas y abrir PRs en este repo, o solo leerlo? Si solo lo lee, deja tus notas en el chat con Benja y yo las anoto aquí.
