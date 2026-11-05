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
| B01 | ia-red exige seguir de verdad (Voluntad o MoveState y desplazamiento); el runner compara con el manifiesto exacto de casos (F19) | Sol | `server/command/Pruebas.java`, `scripts/ci/*` | en PR #2 (incluye B01 y reemplaza al PR #1) |
| B02 | `CommandPolicy` común para menú, paquetes, chat local y modelo; `villagerChatAIUseTools` se respeta (F02, F06) | Sol | `network/c2s/*`, `Config.java`, la puerta de acciones de `chatAI/*` | en PR #2 · sol/contratos-persistencia |
| B03 | Paquetes con codecs explícitos en vez de serialización Java (F01) | Sol | `cobalt/network/*`, `*/NetworkHandlerImpl.java` | en PR #2 · sol/contratos-persistencia |
| B10 | El robo de cofre necesita el actor real del inventario (F07) | Sol | `body/Cofres.java`, `mixin/MixinScreenHandler.java` | en PR #2 · sol/contratos-persistencia |
| F22 | Hambre y encargo sobreviven a reiniciar | Claude | `ProspectingTask.java`, `mente/Voluntad.java`, `mente/Encargo.java`, `brain/VillagerBrain.java` | hecha (3cc19af84) |
| B11 | Una respuesta vieja no actúa: cada turno guarda mundo, dimensión, UUID, `Voluntad.generacion` y vencimiento, y se descarta entera si algo cambió; reintentar no repite el efecto (F10, F11, F15; V14, V15 del informe de vida) | Sol | `chatAI/ChatAI.java`, `chatAI/OpenAIChatAI.java`, `chatAI/Cerebros.java` | en PR #2 · sol/contratos-persistencia |
| F16 | Crafteo con las recetas reales (`RecipeManager`) | libre | `obra/Taller.java`, `body/Progreso.java` | libre |
| F17 | Recogida que respeta demora y dueño del objeto | libre | `body/Hands.java` | libre |
| F18 | Sin teletransporte al seguir en modo estricto | libre | `FollowTask.java`, `EscortTask.java`, `FamilyRescueTask.java` | libre |
| F20, F21 | Versión con SHA en cada build y una línea `[Tropeles] jar <sha>` al arrancar; rangos de versión reales en los metadatos | Sol | `.github/workflows/forge-tropeles.yml`, `mods.toml`, `fabric.mod.json` | en PR #2 · sol/contratos-persistencia |
| B12 | Planificador justo, cuotas de chunks, métricas (F12, F13) | libre | `Pantalla.java`, `ProspectorPresence.java` | libre |
| F24 | Registro de bloques sin obsoletos | libre | `TropelesMundo.java` | libre |
| V06, V29 | El archivo nunca pierde una copia nueva: revisión monotónica por mente, archivo truncado o ilegible da error visible y se queda la otra copia; prueba con más de 256 aldeanos y reinicio | Sol | `server/world/data/ArchivoTropeles.java` (cedido por Claude), `scripts/ci/*`, `server/command/Pruebas.java` | en PR #2 · sol/contratos-persistencia |
| V20, V21 (pruebas) | Escenarios del miedo con lugar y del vínculo con historia, con su control negativo y su fila en `expected-checks.tsv` | Sol | `scripts/ci/*`, `server/command/Pruebas.java` | en PR #2 · sol/contratos-persistencia |
| GEN | `Voluntad.generacion(uuid)` y `Voluntad.nuevaGeneracion(uuid)` para B11 | Claude | `mente/Voluntad.java`, `mente/Interrupcion.java` | hecha (b423e5654) |
| V20 | Miedo con lugar: los peligros que ya cree (`peligro:` del Núcleo) cambian dónde empieza una mina, hacia dónde explora y qué presa caza; avisa al que sigue; pasar seguro por ahí lo baja | Claude | `nucleo/*`, `ProspectingTask.java`, `body/Explorar.java`, `mente/Iniciativa.java` | hecha (b48333aac) |
| V21 | Vínculo con historia: confianza, familiaridad y conflicto (de sus episodios) más los corazones de MCA deciden si acepta, refunfuña o se niega, con la razón; si el que sigue le pega otra vez, deja de seguirlo; disculpas y regalos reparan; una orden no crea cariño | Claude | `mente/Voluntad.java`, `mente/Companero.java`, `nucleo/*`, `chatAI/MenteLocal.java` | hecha (b48333aac) |
| R83 | Resultados tipados de encargos (HECHO, PARCIAL, BLOQUEADO, CANCELADO, VENCIDO, DENEGADO, FALLIDO) con motivo y lo entregado de verdad, en su memoria y en el log | Claude | `mente/Encargo.java`, `mente/Voluntad.java`, `ProspectingTask.java` | hecha (b48333aac) |

## 2026-11-04 · nueva entrega autorizada por Benja

Reparto operativo: [PLAN-PARA-CLAUDE.md](PLAN-PARA-CLAUDE.md), publicado inicialmente en `sol/mineria-retorno`. Estas reservas aplican a la primera entrega nueva y reemplazan el reparto hipotético de los informes en esos archivos. No reabren trabajos ya integrados del PR #2.

| ID | Qué | Quién | Archivos reservados | Estado |
|---|---|---|---|---|
| N01-N05 · primer incremento | Regreso de minas, corredor protegido, búsqueda continuable y costes/diagnóstico; resto de arquitectura pendiente | Sol | `body/Mina.java`, `body/Motor.java`, `mente/Recuerdos.java`, `ProspectingTask.java`, módulos nuevos de navegación | en PR #4 · 60/60 + reinicio 2/2; ver ENTREGA-SOL-MINERIA.md |
| V01-mina | Regresiones de navegación y registro central de escenarios | Sol | `server/command/Pruebas.java`, `scripts/ci/*`, pruebas nuevas de navegación | en PR #4 · 60/60 + 2/2 |
| F16-recetas | Recetas reales y cadena heno→trigo→pan; conservar contratos usados por el minero | Claude | `body/Progreso.java`, `obra/Taller.java`, `entity/ai/crafting/*`, archivo nuevo de pruebas de recetas | asignada por Benja; esperar confirmación de rama de Claude |

La construcción editable, proyectos propios y cuotas se secuencian después según la hoja; todavía no se reservan sus archivos. Claude registra novedades de recetas en `colaboracion/CLAUDE-RECETAS.md` y comunica a Sol los casos que necesitan alta en el CI central.

Evidencia y límites del primer incremento de Sol: [ENTREGA-SOL-MINERIA.md](ENTREGA-SOL-MINERIA.md). Las reservas de navegación/CI permanecen hasta que entre PR #4; Claude mantiene F16 en sus archivos separados.

## 2026-11-04 · construcción: reparto vigente

Reservas publicadas en [CONSTRUCCION-PARA-CLAUDE.md](CONSTRUCCION-PARA-CLAUDE.md), rama `sol/planos-persistentes`, base combinada `d803646`. Sustituyen las propuestas anteriores para construcción; las correcciones de minería/recetas permanecen intactas.

| ID | Qué | Quién | Archivos reservados | Estado |
|---|---|---|---|---|
| C01 | Plano inmutable, identidad/revisión/hash, snapshot, migración y anclas propias | Sol | `obra/Plano.java`, `Obra.java`, `Sitio.java`, `ObrasMundo.java`, nuevo `PlanoCodec.java`, `PruebasPlanos.java`, registro/CI | en PR #7 · 78/78 + reinicio 3/3 |
| C02 | Catálogo local, dos geometrías nuevas y edición limitada de borradores | Claude | nuevos `obra/ModelosCasa.java`, `CatalogoPlanos.java`, `PruebasCatalogo.java`, `CLAUDE-CONSTRUCCION.md` | en PR #8 · 84/84 + reinicio 3/3 con las seis registradas (rama temporal) |
| C03a | Puerta completa, bloques y madera exactos, suministro/fabricación por especie, selección básica de diseño y regresiones | Sol | `obra/Constructor.java`, `Plano.java`, `Obra.java`, `Taller.java`, nuevo `PedidoCasa.java`; cambios acotados en `body/Progreso.java`, `mente/Voluntad.java`, `chatAI/TriggerCommandInfos.java`; pruebas/CI | en PR #9 · 91/91 + reinicio 3/3; entrega para revisión/JAR en SOL-CONSTRUCCION-FIEL.md |
| C03b | Propuesta, edición conversacional, vista previa y aprobación de revisión | Claude | nuevos módulos de propuestas, Proyeccion y entrada de conversación, después de revisar C03a; conservar los contratos del plano fijo | en PR #10, borrador; siete casos y reinicio aún por registrar en el árbol combinado |

Benja reasignó C03a a Sol; el alcance se publicó antes del código en [SOL-CONSTRUCCION-FIEL.md](SOL-CONSTRUCCION-FIEL.md) y en el PR #8. Sol no modifica modelos/catálogo ni render; Claude conserva C03b y evita duplicar los arreglos reservados hasta revisar la entrega. El PR #9 se apila sobre #8; #8 sobre #7 y #7 sobre #6. No fusionar trabajo ni publicar jar sin la decisión de Benja.

## 2026-11-05 · memoria y minería

Benja autorizó revisar, investigar y corregir fallos de autonomía. Alcance publicado antes del código en [SOL-MEMORIA-MINERIA.md](SOL-MEMORIA-MINERIA.md). Base #9; esta entrega es paralela a C03b de Claude en #10, sin editar sus archivos de propuestas, conversación, catálogo o render. Validación final del código: **100/100 + reinicio 4/4**, builds Forge/Fabric aprobados; evidencia y límites en la hoja.

| ID | Qué | Quién | Archivos reservados | Estado |
|---|---|---|---|---|
| M01 | Diario paginado sin descarte rutinario por el límite de 300; identidades, recuperación, lectura y fallo visible; repetición situada | Sol | `mente/Recuerdos.java`, `PruebasRecuerdos.java`, nuevos `CronicaTropeles.java`/`PruebasCronica.java`, `ArchivoTropeles.java` | en PR #11 |
| M02 | Incidentes separados por lugar/dimensión/recurso, origen UUID completo y memoria permanente sin overflow | Sol | `nucleo/Mente.java`, `PruebasIncidentes.java`, `LongTermMemory.java` | en PR #11 |
| N06 | Pies sobre apoyos parciales; búsqueda continuable en RETURN; no reiniciar una respuesta NO_PATH en el mismo tick | Sol | `body/Mina.java`, `Mining.java`, `Motor.java`, `ProspectingTask.java` | en PR #11 |
| F26 | Respuesta HTTP de hasta 1 MiB, cierre/desconexión y plazo total GET | Sol | `chatAI/Cerebros.java`, nuevos `RespuestaAcotada.java`/`PruebasRespuesta.java` | en PR #11 |
| V30 | Pruebas de diario/reinicio, fallos de archivo, minería física, camino bloqueado, HTTP y liberación de tickets propios | Sol | `PruebasAutonomia.java`, nuevo `PruebasChunks.java`, registro central y `scripts/ci/*`, filtro Forge | en PR #11 |

El cambio puntual de `Constructor.java` solo pide la especie de madera requerida, como pidió Claude al revisar #9. Se conservan los contratos de construcción fiel. La crónica conserva descripciones del diario; no elimina límites de pasos, marcas o episodios del Núcleo ni reconstruye datos ya borrados. Evidencia final y continuación en la hoja de entrega. Sin merge ni release automáticos.
