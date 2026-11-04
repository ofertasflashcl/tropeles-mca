# Construcción: tareas de Claude y Sol

Benja autorizó implementar desde `tropeles-construccion-catalogos-e-internet.md`, que nos entregará a ambos. Esta hoja es el reparto operativo; reemplaza el reparto hipotético anterior para construcción.

## Base y ramas

- Base comprobada: `tropeles/retorno-y-recetas`, `d803646afe69c9896659f4ddd30874f00091f30a` (PR #6, todavía abierto). Contiene minería y F16; no trabajar desde main ni desde el jar antiguo de la release.
- Sol: `sol/planos-persistentes`. PR apilado contra `tropeles/retorno-y-recetas` para no repetir el diff de minería/recetas. No fusionar ni publicar jar de integración sin la decisión de Benja.
- Claude: crea `tropeles/claude-catalogo-casas` desde la misma base. Puedes empezar ahora con los modelos y el catálogo; incorpora el PR de Sol al conectar sus interfaces. No copies ni inventes una segunda implementación del contrato.
- No APIs pagadas. Internet e importación externa quedan después del catálogo local verificable. No recopilar catálogos de terceros.

## C01 · Sol: plano persistente y validación (tomada)

Archivos reservados, bajo `common/src/main/java/net/mca/`:

- `entity/ai/obra/Plano.java`, `Obra.java`, `Sitio.java`, `ObrasMundo.java`.
- Nuevo `entity/ai/obra/PlanoCodec.java`.
- Nuevo `server/command/PruebasPlanos.java`, alta mínima en `Pruebas.java` y `scripts/ci/*`.

Entrega: plano inmutable con identidad/revisión/hash, anclas propias, validación acotada, snapshot completo en el guardado y migración de obras viejas sin cambiar su geometría. Un plano inválido o de versión desconocida no se convierte silenciosamente en otra casa. No editaré tus archivos de diálogo, render ni modelos.

Contrato que implementa Sol (Java/Yarn 1.20.1; sin nuevas dependencias):

```java
// Plano: se conservan casa(Madera), Pieza, Rol, piezas y materiales.
public final String id;
public final int revision;
public final BlockPos centro;  // ancla del suelo, y=0
public final BlockPos entrada; // pies fuera de la puerta, local
public static Plano crear(String id, int revision, String nombre, String nombreEn,
        BlockPos centro, BlockPos entrada, List<Plano.Pieza> piezas);
public String hash();

// Obra: se conserva el constructor anterior y plano().
public Obra(String id, UUID constructor, String constructorNombre,
        BlockPos origen, Direction frente, Plano.Madera madera, Plano plano);
```

El plano de una obra iniciada queda fijado. Para editar un borrador, crear otro `Plano`; no mutar listas, no cambiar un plano aprobado en sitio ni añadir setters de geometría a `Obra`. La madera sigue siendo el parámetro de suministro actual: elige una paleta de esa madera en esta primera entrega.

El subconjunto inicial usa exclusivamente los roles actuales y estados vanilla que su colocador ya admite. El factory rechaza datos vacíos, duplicados o fuera de límites; el codec guarda estados por identificador y propiedades, no raw IDs de una sesión. Límites iniciales: 4.096 celdas y extensión máxima de 64 bloques por eje. Los nombres y firmas de arriba son el acuerdo de integración; cualquier cambio se documenta antes de consumirlo.

## C02 · Claude: tres casas y catálogo local (asignada; empieza ahora)

Archivos reservados:

- Nuevos `entity/ai/obra/ModelosCasa.java` y `CatalogoPlanos.java` (o subpaquete nuevo de modelos, sin tocar Plano).
- Nuevo `server/command/PruebasCatalogo.java`.
- `colaboracion/CLAUDE-CONSTRUCCION.md` para responder con rama, SHA, casos y dudas.

Trabajo concreto, en este orden:

1. Mantener la casa actual como opción. Añadir dos geometrías realmente diferentes: una cabaña de un piso y una casa alargada con otra distribución/cubierta. Diseños propios, pequeños, construibles con los roles existentes; no ocho maderas presentadas como ocho casas.
2. Generar cada diseño con `Plano.crear(...)`, ID estable, revisión, centro y entrada reales. Declarar anclas y todas las celdas de aire necesarias, acceso y escalera cuando corresponda. No añadir camas, losas, bloques especiales o mecanismos antes de tener colocador probado.
3. Catálogo determinista por ID y filtros simples de tamaño/madera, con metadatos y nombres ES/EN. Sin HTTP, JEI obligatorio, modelo de pago ni generación de código ejecutable.
4. Preparar una operación de edición limitada sobre borradores: cambiar dos ventanas o elegir una cubierta alternativa compatible. Devuelve otro plano/revisión y una diferencia de piezas real. No editar obras activas.
5. Pruebas en archivo separado: tres geometrías distintas, cuatro orientaciones, límites, entrada despejada, invariantes de puerta/escalera y edición con diferencia efectiva. Envía a Sol los nombres exactos de escenarios para registrarlos; no edites el registro ni el manifiesto de CI.
6. Antes de cerrar, inspecciona si `Constructor` asume la casa fija y anota cada bloqueo concreto. El catálogo no se anuncia como construible hasta probar que un aldeano ejecuta sus piezas; un render o un JSON no basta.

Tu primer PR puede contener modelos, catálogo y pruebas. Compílalo sobre el contrato de Sol al integrar. No esperes un proveedor online para entregar esto.

## C03 · Claude: selección, propuesta y vista previa (siguiente PR, después de C02)

Archivos reservados para ese incremento: `mente/Voluntad.java` (solo entrada de casa), `chatAI/MenteLocal.java`, `Acciones.java`, `TriggerCommandInfos.java`, `obra/Proyeccion.java`, `Constructor.java`, mensajes/vistas de construcción y nuevos módulos de propuestas.

- Elegir una de las casas por conversación y mostrar exactamente su plano.
- Pedir la edición soportada y mostrar la diferencia y materiales reales/estimados sin confundirlos.
- Guardar propuesta pendiente; aprobar desde la identidad autenticada del jugador. Solo esa revisión crea la `Obra`. El modelo no puede autoaprobar.
- Cancelar, reanudar y comprobar reinicio. Conservar cambios posteriores del jugador; todavía no reformar una casa ocupada.
- Mantener firmas de navegación/recetas y permisos existentes. No tocar `ProspectingTask`, `Motor`, `Mina`, `Progreso` ni reescribir los codecs compartidos.
- Paquetes nuevos y cambios de formato se acuerdan antes de tocar el registro común. Sin tocar archivos reservados a Sol.

## Cierre y revisión

Claude: responde primero en `CLAUDE-CONSTRUCCION.md` con tu rama y toma C02. Abre un PR pequeño y entrega evidencia de lo ejecutado, límites y necesidades de integración. Sol revisa datos/persistencia/consumo; tú revisas su núcleo y la experiencia de juego.

Sol mantiene el registro de pruebas. Al unir ramas se prueba el árbol combinado; verde por separado no prueba integración. No force-push, no sobrescribir trabajo ajeno. Benja decide el merge y prueba el jar.

**Benja ya puede decirte:** «Lee `colaboracion/CONSTRUCCION-PARA-CLAUDE.md` en `sol/planos-persistentes`, junto al informe adjunto; comienza C02 y responde en `CLAUDE-CONSTRUCCION.md`. Sol toma C01».
