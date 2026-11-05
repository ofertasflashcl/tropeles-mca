# Sol: memoria duradera y minería · 2026-11-05

Benja autorizó analizar, investigar y mejorar minería, memoria y control de los aldeanos. Base estable: `sol/construccion-fiel` en `2521591ce3bc0af7ccae79e13bd86e0f285be9e9`. Rama de trabajo: `sol/autonomia-memoria-mineria`; PR hacia esa base. No se integra ni se publica JAR automáticamente.

## Reserva de Sol antes del código

- `mente/Recuerdos.java`, nuevo archivo de crónica paginada y cambios acotados en `server/world/data/ArchivoTropeles.java`: conservar el diario que hoy se elimina al superar 300 entradas, con RAM acotada, identidad/orden estable, recuperación de transacciones y consulta paginada. El almacenamiento físico sigue siendo finito; no se promete memoria matemática infinita ni recuperar recuerdos ya borrados.
- `body/Mina.java`, `body/Mining.java`, `body/Motor.java`, `brain/tasks/chore/ProspectingTask.java`: unificar los pies sobre losas/escaleras y continuar las búsquedas hacia el frente de trabajo, sin confundir cuota agotada con ruta imposible.
- `nucleo/Mente.java`: separar incidentes de dimensiones distintas y evitar identificadores abreviados que puedan colisionar entre aldeanos. `LongTermMemory.java`: corregir el recuerdo «para siempre» de MCA y la suma de vencimientos.
- Cambio puntual en `obra/Constructor.java`: nombrar la especie al pedir material, como señaló Claude en #9. No cambiar geometrías ni su interfaz C03b.
- F26 en `chatAI/Cerebros.java` y lector/pruebas separados: respuestas HTTP limitadas a 1 MiB, cierre en éxito/fallo y plazo total también para GET. Reserva ampliada públicamente en el PR #11 antes del código.
- Pruebas físicas, archivo/reinicio y registro central `Pruebas.java`/`scripts/ci/*`; permitir CI Forge para PRs hacia `sol/**` y evitar ramas temporales de pruebas.

Claude conserva C03b en #10: propuestas, conversación, aprobación, catálogo y render. No se modifican esos archivos en esta entrega. La investigación contrastará el código con fuentes primarias sobre memoria jerárquica, información qué/dónde/cuándo, búsqueda continuable y recuperación de archivos; se entregarán hallazgos, cambios medidos y límites. El revisor externo de la sesión anterior sigue sin instalar; no se reintenta el instalador rechazado ni se atribuyen resultados a él.

## Implementación entregada en PR #11

- `CronicaTropeles`: páginas NBT de 256 eventos por UUID y dimensión codificada sin alias. Lote preparado en índice, aplicación idempotente y confirmación antes de desalojar la copia reciente. Rechaza identidad con otro contenido, versiones desconocidas y archivos existentes ilegibles; comprueba revisiones mediante `ArchivoTropeles`.
- `Recuerdos`: secuencia independiente del tiempo, migración del NBT antiguo, consulta por cursor y recuperación por tipos mediante la API existente. Caché objetivo 300, lotes de 64 y checkpoint de la cola parcial. Repeticiones requieren mismo lugar e intervalo no negativo. Si falla el disco, se retienen los anteriores, se espacian reintentos y se contabilizan explícitamente eventos nuevos que no caben al llegar al búfer de 556.
- `Mina`/`Mining`: mismo apoyo que el navegador, incluidos losas y peldaños bajos. `RETURN` continúa la búsqueda entre ticks, no cava por agotar una cuota y no activa el rescate de atasco mientras calcula. La respuesta NO_PATH queda brevemente disponible para la misma pregunta, para que el controlador no la vuelva a iniciar en el mismo tick.
- `Mente`: agrupa incidentes por dimensión y celda espacial; distingue recursos y conserva UUID completo de origen. `LongTermMemory`: permanente real mediante centinela, vencimiento exacto y suma/resta saturadas.
- `Cerebros`: máximo de 1 MiB por cuerpo de respuesta, también sin Content-Length y para errores; streams cerrados y conexión desconectada; GET tiene plazo total. No cambia proveedores ni requiere claves nuevas.
- `Constructor`: pide explícitamente la madera correcta; conserva colocación, recetas y contrato exacto de #9. CI Forge acepta bases `sol/**`; se añaden escenarios y manifiestos exactos.

## Evidencia de cierre

[Forge/servidor 41989275998](https://github.com/ofertasflashcl/tropeles-mca/actions/runs/41989275998), job `127117358227`: **100/100 + segundo proceso 4/4**, con arranques/cierres limpios. **900/900 recuerdos** recuperados tras reinicio sin recrearlos; primero y charla antigua consultables; caché 300. Corredor de 140 bloques recorrido físicamente con cuota 1, espera 184 ticks y cero bloques alterados; control cerrado termina la espera. La batería libera **1.351 tickets de chunks propios** antes de guardar; la pasada final no usa perfilador.

[Builds Forge/Fabric 41989276040](https://github.com/ofertasflashcl/tropeles-mca/actions/runs/41989276040), job `127117358008`: ambos aprobados. Código probado `eb372950e16f8020c5db18eb5057db7be2da9410`; merge de prueba estampado `1bbe27a479861a8b62539378ac2114cb9c77cb1c`. Ambos comparten árbol `b3d04afb1c8e8a47fe50c6157e1d462b301285d1`, comprobado por API y árbol local. Sintaxis Java 17 seleccionada, Bash y verificador local 3/3 correctos. El cierre posterior solo modifica colaboración con `[skip ci]`.

En los intentos intermedios el corredor abierto y los 900 recuerdos ya aprobaban. La prueba negativa primero era demasiado amplia; tras acotarla, el log mostró NO_PATH en 20 celdas y un reinicio inmediato de la misma consulta. Se corrigió conservando brevemente esa respuesta terminal. No se elimina el control negativo ni se relaja la exigencia física para obtener el verde.

El primer proceso llegó a 100/100, pero el reinicio agotó el heap antes de recibir comandos. Reducir el área del corredor y fijar 2 GiB no bastó. El perfil mostró unos dos millones de `CompletableFuture` durante la carga de chunks: el ayudante general `Pruebas.load` dejaba tickets permanentes guardados en el mundo. `PruebasChunks` rastrea y libera los añadidos por los ensayos al terminar el resumen; conserva los forzados preexistentes. También se limpian escenarios pendientes antes de soltar sus recursos. El corredor usa un rectángulo y sus tickets propios.

El lanzador fija 512 MiB inicial/2 GiB máximo y ExitOnOutOfMemoryError; conserva consolas y tamaños del mundo. El perfilador temporal se retiró para la validación final normal. Esto no demuestra ausencia de fugas en una partida larga ni sustituye mediciones de población activa.

La prueba de 140 bloques es **RETURN**, entrada → frente, con seis niveles de bajada; no es una subida de 140 bloques. La batería conserva además la subida física por seis escalones y el pozo con agua. `mina-pies` usa posición asistida para comprobar apoyos/NBT; el recorrido de 140 bloques utiliza locomoción real, sin recolocar al actor durante la prueba. El caso de memoria tras reinicio solo lee los datos del primer proceso.

## Límites reales

El archivo conserva **descripciones del diario**, con el recorte anterior de 240 caracteres más elipsis y agrupación de repeticiones. No registra cada mensaje/fotograma completo ni reconstruye recuerdos ya borrados. Los 400 episodios/96 creencias del Núcleo, 30.000 pasos y 2.048 marcas siguen teniendo sus límites propios. La lectura por tipos considera como máximo 1.024 candidatos; el historial completo puede recorrerse con cursor.

No hay almacenamiento matemáticamente infinito, garantía de fsync/corte eléctrico ni recuperación de páginas borradas. La prueba de transacción inyecta estados preparados y truncamiento reales, no corta la energía. Las escrituras/lecturas del archivo siguen siendo síncronas; falta medir el coste con muchos aldeanos activos. Sin validación nueva de cliente/modpack de Benja o carga masiva. El límite HTTP se prueba sobre el lector de producción; no cubre toda combinación de DNS/TLS/proveedor.

Los eventos recientes que aún no entraron en un checkpoint del mundo pueden perderse ante un cierre abrupto. Conservar el archivo antiguo no equivale a confirmar durablemente cada nuevo evento. La siguiente API debería distinguir observaciones agrupables de promesas/acuerdos que deban registrarse con contenido completo antes de confirmarlos al jugador.

## Para Claude

1. Revisar #11 sobre #9. Evitar duplicar este archivo de memoria o su controlador minero en C03b.
2. Conservar tu #10 como entrega independiente. En el árbol combinado, Sol dará de alta `PruebasPropuestas`: `case "propuestas", "propuestas-reinicio"`, sus siete nombres de `NOMBRES`, comando `propuestas` antes de await y `propuestas-reinicio` antes del await del segundo proceso. Suite combinada prevista **107 + 5**, todavía no ejecutada; no confundir un archivo de pruebas existente con una prueba ya registrada.
3. Tras la decisión de Benja, combinar #6 → #7 → #8 → #9 y ambas ramas #10/#11, ejecutar el CI del árbol resultante y compilar el JAR de ese árbol. Comprobar el SHA de arranque; el enlace público anterior no incorpora automáticamente PRs abiertos.
4. Siguiente división propuesta: Sol, índice reconstruible de memoria por tipo/entidad/lugar y rutas críticas reversibles; Claude, recuperación aplicada al diálogo y propuestas/ampliaciones de casas. Primero acordar la API para no tocar el mismo archivo a la vez.

La investigación contrasta MemGPT, MrSteve, Generative Agents, recuperación de contextos largos, búsquedas incrementales, incidencias de navegación y garantías de archivos. Es una guía de mecanismos aplicables; no propone instalar esas pilas completas en MCA ni cargar un modelo distinto por aldeano. Informe completo preparado para Benja. Sin merge ni release en esta sesión.

Fuentes primarias para continuar: [MemGPT](https://arxiv.org/abs/2310.08560), [MrSteve](https://arxiv.org/abs/2411.06736), [Generative Agents](https://arxiv.org/abs/2304.03442), [D* Lite](https://idm-lab.org/bib/abstracts/Koen02e.html), [Lost in the Middle](https://aclanthology.org/2024.tacl-1.9/) y [su contraste posterior en preguntas factuales](https://research.google/pubs/retrieval-quality-at-context-limit/). SQLite [WAL](https://sqlite.org/wal.html) inspira la separación preparación/confirmación; no se implementó SQLite ni se heredaron sus garantías de durabilidad.
