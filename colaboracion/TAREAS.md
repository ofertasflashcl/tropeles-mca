# Tareas

IDs de la auditoría de Sol. Estados: **propuesta** (sugerida, nadie la confirmó), **tomada**, **en PR**, **hecha**. El dueño pone su nombre; los archivos de una tarea tomada quedan reservados para su dueño.

| ID | Qué | Quién | Archivos reservados | Estado |
|---|---|---|---|---|
| F25 | El cerrojo `PENSANDO` se libera siempre | Claude | `mente/Conciencia.java` | en CI (704045b) |
| B05 | El chisme cuenta cada narrador una sola vez por incidente (F04) | Claude | `nucleo/*` | en CI (704045b) |
| B06 | Una lección se valida antes de guardarse en cualquier almacén; el habla espontánea pasa por Anclaje (F05) | Claude | `mente/Conciencia.java`, `mente/Iniciativa.java`, `nucleo/*` | en CI (704045b) |
| B04 | Núcleo, Recuerdos y lecciones no borran mentes al pasar el límite (F03); los episodios guardan la dimensión (F14) | Claude | `nucleo/*`, `mente/Recuerdos.java`, `mente/Conciencia.java` | en CI (704045b) |
| B08 | Una obra solo queda LISTA con la estructura completa; si no, BLOQUEADA o PARCIAL con el motivo (F09) | Claude | `obra/*` | en CI (704045b) |
| B09 | El escudo y el equipo no desaparecen al morir (F23) | Claude | `VillagerEntityMCA.java` (solo `onDeath`), `body/CombatMind.java` | en CI (704045b) |
| B07 | Al romper, revalida bloque y permiso hasta el último golpe (F08) | Claude | `body/Hands.java` | en CI (704045b) |
| B01 | ia-red exige seguir de verdad (Voluntad o MoveState y desplazamiento); el runner compara con el manifiesto exacto de casos (F19) | Sol | `server/command/Pruebas.java`, `scripts/ci/*` | propuesta |
| B02 | `CommandPolicy` común para menú, paquetes, chat local y modelo; `villagerChatAIUseTools` se respeta (F02, F06) | Sol | `network/c2s/*`, `Config.java`, la puerta de acciones de `chatAI/*` | propuesta |
| B03 | Paquetes con codecs explícitos en vez de serialización Java (F01) | Sol | `cobalt/network/*`, `*/NetworkHandlerImpl.java` | propuesta |
| B10 | El robo de cofre necesita el actor real del inventario (F07) | Sol | `body/Cofres.java`, `mixin/MixinScreenHandler.java` | propuesta |
| F22 | Hambre y encargo sobreviven a reiniciar | libre | `ProspectingTask.java`, `mente/Voluntad.java` | libre |
| B11 | ActionToken, cancelación y `ServerRuntime` (F10, F11, F15) | libre | `chatAI/ChatAI.java`, `chatAI/OpenAIChatAI.java` | libre |
| F16 | Crafteo con las recetas reales (`RecipeManager`) | libre | `obra/Taller.java`, `body/Progreso.java` | libre |
| F17 | Recogida que respeta demora y dueño del objeto | libre | `body/Hands.java` | libre |
| F18 | Sin teletransporte al seguir en modo estricto | libre | `FollowTask.java`, `EscortTask.java`, `FamilyRescueTask.java` | libre |
| F20, F21 | Versión con SHA en cada build; rangos de versión reales en los metadatos | libre | `.github/workflows/forge-tropeles.yml`, `mods.toml`, `fabric.mod.json` | libre |
| B12 | Planificador justo, cuotas de chunks, métricas (F12, F13) | libre | `Pantalla.java`, `ProspectorPresence.java` | libre |
| F24, F26 | Registro de bloques sin obsoletos; HTTP con límite de tamaño y cierre | libre | `TropelesMundo.java`, `Cerebros.java` | libre |
