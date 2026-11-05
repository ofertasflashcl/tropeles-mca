# Sol: memoria duradera y minería · 2026-11-05

Benja autorizó analizar, investigar y mejorar minería, memoria y control de los aldeanos. Base estable: `sol/construccion-fiel` en `2521591ce3bc0af7ccae79e13bd86e0f285be9e9`. Rama de trabajo: `sol/autonomia-memoria-mineria`; PR hacia esa base. No se integra ni se publica JAR automáticamente.

## Reserva de Sol antes del código

- `mente/Recuerdos.java`, nuevo archivo de crónica paginada y cambios acotados en `server/world/data/ArchivoTropeles.java`: conservar el diario que hoy se elimina al superar 300 entradas, con RAM acotada, identidad/orden estable, recuperación de transacciones y consulta paginada. El almacenamiento físico sigue siendo finito; no se promete memoria matemática infinita ni recuperar recuerdos ya borrados.
- `body/Mina.java`, `body/Mining.java`, `body/Motor.java`, `brain/tasks/chore/ProspectingTask.java`: unificar los pies sobre losas/escaleras y continuar las búsquedas hacia el frente de trabajo, sin confundir cuota agotada con ruta imposible.
- `nucleo/Mente.java`: separar incidentes de dimensiones distintas y evitar identificadores abreviados que puedan colisionar entre aldeanos. `LongTermMemory.java`: corregir el recuerdo «para siempre» de MCA y la suma de vencimientos.
- Cambio puntual en `obra/Constructor.java`: nombrar la especie al pedir material, como señaló Claude en #9. No cambiar geometrías ni su interfaz C03b.
- Pruebas físicas, archivo/reinicio y registro central `Pruebas.java`/`scripts/ci/*`; permitir CI Forge para PRs hacia `sol/**` y evitar ramas temporales de pruebas.

Claude conserva C03b en #10: propuestas, conversación, aprobación, catálogo y render. No se modifican esos archivos en esta entrega. La investigación contrastará el código con fuentes primarias sobre memoria jerárquica, información qué/dónde/cuándo, búsqueda continuable y recuperación de archivos; se entregarán hallazgos, cambios medidos y límites. El revisor externo de la sesión anterior sigue sin instalar; no se reintenta el instalador rechazado ni se atribuyen resultados a él.
