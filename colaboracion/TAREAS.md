# Tareas

IDs de la auditoría de Sol (F, B) y de su informe de vida autónoma (V = matriz de su apartado 10.1, R83 = apartado 8.3). Estados: **propuesta** (sugerida, nadie la confirmó), **asignada** (se la pidieron a ese dueño; la pasa a tomada cuando empieza), **tomada**, **en PR**, **hecha**. El dueño pone su nombre; los archivos de una tarea tomada quedan reservados para su dueño.

| ID | Qué | Quién | Archivos reservados | Estado |
|---|---|---|---|---|
| F25 | El cerrojo `PENSANDO` se libera siempre | Claude | `mente/Conciencia.java` | hecha (704045b) |
| B05 | El chisme cuenta cada narrador una sola vez por incidente (F04) | Claude | `nucleo/*` | hecha (704045b) |
| B06 | Una lección se valida antes de guardarse en cualquier almacén; el habla espontánea pasa por Anclaje (F05) | Claude | `mente/Conciencia.java`, `mente/Iniciativa.java`, `nucleo/*` | hecha (704045b) |
| B04 | Núcleo, Recuerdos y lecciones no borran mentes al pasar el límite (F03); los episodios guardan la dimensión (F14) | Claude | `nucleo/*`, `mente/Recuerdos.java`, `mente/Conciencia.java` | hecha (704045b) |
| B08 | Una obra solo queda LISTA con la estructura completa; si no, BLOQUEADA o PARCIAL con el motivo (F09) | Claude | `obra/*` | hecha (704045b) |
| B09 | El escudo y el equipo no desaparecen al morir (F23) | Claude | `VillagerEntityMCA.java` (solo `onDeath`), `body/CombatMind.java` | hecha (704045b) |
| B07 | Al romper, revalida bloque y permiso hasta el último golpe (F08) | Claude | `body/Hands.java` | hecha (704045b) |
| B01 | ia-red exige seguir de verdad (Voluntad o MoveState y desplazamiento); el runner compara con el manifiesto exacto de casos (F19) | Sol | `server/command/Pruebas.java`, `scripts/ci/*` | en PR (rama `sol/b01`) |
| B02 | `CommandPolicy` común para menú, paquetes, chat local y modelo; `villagerChatAIUseTools` se respeta (F02, F06) | Sol | `network/c2s/*`, `Config.java`, la puerta de acciones de `chatAI/*` | propuesta |
| B03 | Paquetes con codecs explícitos en vez de serialización Java (F01) | Sol | `cobalt/network/*`, `*/NetworkHandlerImpl.java` | propuesta |
| B10 | El robo de cofre necesita el actor real del inventario (F07) | Sol | `body/Cofres.java`, `mixin/MixinScreenHandler.java` | propuesta |
| F22 | Hambre y encargo sobreviven a reiniciar | Claude | `ProspectingTask.java`, `mente/Voluntad.java`, `mente/Encargo.java`, `brain/VillagerBrain.java` | hecha (3cc19af84) |
| B11 | Una respuesta vieja no actúa: cada turno guarda mundo, dimensión, UUID, `Voluntad.generacion` y vencimiento, y se descarta entera si algo cambió; reintentar no repite el efecto (F10, F11, F15; V14, V15 del informe de vida) | Sol | `chatAI/ChatAI.java`, `chatAI/OpenAIChatAI.java`, `chatAI/Cerebros.java` | asignada (1.º) |
| F16 | Crafteo con las recetas reales (`RecipeManager`) | libre | `obra/Taller.java`, `body/Progreso.java` | libre |
| F17 | Recogida que respeta demora y dueño del objeto | libre | `body/Hands.java` | libre |
| F18 | Sin teletransporte al seguir en modo estricto | libre | `FollowTask.java`, `EscortTask.java`, `FamilyRescueTask.java` | libre |
| F20, F21 | Versión con SHA en cada build y una línea `[Tropeles] jar <sha>` al arrancar; rangos de versión reales en los metadatos | Sol | `.github/workflows/forge-tropeles.yml`, `mods.toml`, `fabric.mod.json` | asignada (4.º) |
| B12 | Planificador justo, cuotas de chunks, métricas (F12, F13) | libre | `Pantalla.java`, `ProspectorPresence.java` | libre |
| F24, F26 | Registro de bloques sin obsoletos; HTTP con límite de tamaño y cierre | libre | `TropelesMundo.java`, `Cerebros.java` | libre |
| V06, V29 | El archivo nunca pierde una copia nueva: revisión monotónica por mente, archivo truncado o ilegible da error visible y se queda la otra copia; prueba con más de 256 aldeanos y reinicio | Sol | `server/world/data/ArchivoTropeles.java` (cedido por Claude), `scripts/ci/*`, `server/command/Pruebas.java` | asignada (3.º) |
| V20, V21 (pruebas) | Escenarios del miedo con lugar y del vínculo con historia, con su control negativo y su fila en `expected-checks.tsv` | Sol | `scripts/ci/*`, `server/command/Pruebas.java` | asignada (5.º), cuando Claude suba su ronda |
| GEN | `Voluntad.generacion(uuid)` y `Voluntad.nuevaGeneracion(uuid)` para B11 | Claude | `mente/Voluntad.java`, `mente/Interrupcion.java` | hecha (ver bitácora) |
| V20 | Miedo con lugar: los peligros que ya cree (`peligro:` del Núcleo) cambian dónde empieza una mina, hacia dónde explora y qué presa caza; avisa al que sigue; pasar seguro por ahí lo baja | Claude | `nucleo/*`, `ProspectingTask.java`, `body/Explorar.java`, `mente/Iniciativa.java` | tomada |
| V21 | Vínculo con historia: confianza, familiaridad y conflicto (de sus episodios) más los corazones de MCA deciden si acepta, refunfuña o se niega, con la razón; si el que sigue le pega otra vez, deja de seguirlo; disculpas y regalos reparan; una orden no crea cariño | Claude | `mente/Voluntad.java`, `mente/Companero.java`, `nucleo/*`, `chatAI/MenteLocal.java` | tomada |
| R83 | Resultados tipados de encargos (HECHO, PARCIAL, BLOQUEADO, CANCELADO, VENCIDO, DENEGADO, FALLIDO) con motivo y lo entregado de verdad, en su memoria y en el log | Claude | `mente/Encargo.java`, `mente/Voluntad.java`, `ProspectingTask.java` | tomada |
