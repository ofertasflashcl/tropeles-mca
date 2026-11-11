package net.mca.entity.ai.obra;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Compiles a bounded block edit from the model into a new immutable blueprint. No world writes. */
public final class EdicionPlano {
    public static final String COMANDO = "design-blueprint";
    public static final String ID = "mca:custom_blueprint";
    public static final int MAX_ARGUMENTO = 4800;
    private static final int MAX_ORDENES = 64;
    private static final int MAX_CELDAS_EDITADAS = Plano.MAX_PIEZAS * 2;

    public static final String LENGUAJE = """
            Separate operations by semicolons. Use local integer x y z, with -Z front, +X east, floor y=0.
            set x y z block[state]; fill x y z x2 y2 z2 block[state] (inclusive); shell x y z x2 y2 z2 block[state] (six faces);
            line x y z x2 y2 z2 block[state]; omit x y z (stop specifying this cell); entry x y z (reserve feet/head air);
            ellipse x y z x2 y2 z2 block[state] (filled oval in one plane); arch x y z x2 y2 z2 block[state]
            (connected upper arch in a vertical plane, endpoints at the lower Y, leave the opening untouched).
            replace x y z x2 y2 z2 oldBlock[state] newBlock[state] (only already specified cells with the exact old state).
            optional reset as the first operation (empty canvas, keep the center). To explicitly clear a cell use set x y z air.
            Blocks: cobblestone, stone, stone_bricks, bricks, glass, glass_pane, torch, wall_torch, crafting_table, chest,
            wood logs/planks/stairs/doors, stone stairs/slabs, wool, concrete and terracotta. Use real Minecraft IDs
            and optional axis/facing/half/hinge/type properties, e.g. spruce_log[axis=x]. Set a lower wooden door with
            set; the upper half is added automatically. For a new design include a foundation at y=0, a supported entry
            and join all solid pieces to the foundation. Up to 4096 cells. Preserve unrelated cells in edits.
            """;

    /** Identity of the proposal at the time the brain was asked; a null id means it had none. */
    public record Base(@Nullable String id, int version, String descripcion) {
    }

    /** A dry run never changes the draft. An outdated base must not be repaired against a different revision. */
    public record Validacion(boolean vigente, @Nullable String error) {
    }

    private static final Pattern CASA_SIMPLE = Pattern.compile(
            "(?:(?:por favor|oye|ya) )?(?:construye(?:me|nos)?|haz(?:me|nos)?|arma(?:me)?|levanta(?:me)?|build(?: me)?|make(?: me)?) "
                    + "(?:una |un |a |an |my |mi )?(?:casa(?: (?:de )?(?:dos pisos|2 pisos|dos plantas|larga|alargada))?|cabana|refugio|"
                    + "house|log cabin|cabin|long house|two (?:floor|story|storey) house)"
                    + "(?: (?:de |of |in )?(?:roble oscuro|roble|abeto|pino|abedul|jungla|acacia|mangle|cerezo|"
                    + "dark oak|oak|spruce|birch|jungle|mangrove|cherry))?"
                    + "(?: (?:aqui|aca|ahi|here|there))?(?: (?:por favor|please))?");
    private static final Pattern EDITA_SIMPLE = Pattern.compile(
            "(?:(?:ponle|pon|hazle|agrega|anade|quita|mueve|cambia|quiero|add|move|remove) )?(?:(?:un|el|a|the|sin|without) )?"
                    + "(?:balcon|balcony)(?: (?:al frente|atras|detras|a la izquierda|a la derecha|front|back|left|right))?"
                    + "|(?:(?:ponle|pon|cambia|quiero) )?(?:(?:el|un) )?techo (?:mas )?(?:alto|empinado|girado|normal|a dos aguas)"
                    + "|(?:(?:pon|ponle|abre|agrega|anade|cierra|quita) )?(?:(?:una|dos|1|2) )?ventanas?"
                    + "(?: (?:al frente|atras|a la izquierda|a la derecha))?"
                    + "|(?:mejor |prefiero )?(?:una |de )?(?:cabana|casa larga|dos pisos|roble oscuro|roble|abeto|pino|abedul|jungla|acacia|mangle|cerezo)");

    private EdicionPlano() {
    }

    public static boolean esCasaSimple(String mensaje) {
        return CASA_SIMPLE.matcher(CambioCasa.llano(mensaje)).matches();
    }

    public static boolean esCambioSimple(String mensaje) {
        return EDITA_SIMPLE.matcher(CambioCasa.llano(mensaje)).matches();
    }

    public static Plano aplicar(Plano base, Plano.Madera madera, String texto) {
        if (texto.isBlank() || texto.length() > MAX_ARGUMENTO) {
            throw new IllegalArgumentException("empty or oversized block edit");
        }
        String[] lineas = texto.strip().split("[;\\r\\n]+");
        if (lineas.length > MAX_ORDENES) {
            throw new IllegalArgumentException("too many block operations");
        }
        Map<BlockPos, Plano.Pieza> piezas = new LinkedHashMap<>();
        base.piezas.forEach(p -> piezas.put(p.rel(), p));
        BlockPos entrada = base.entrada;
        int tocadas = 0;
        for (int i = 0; i < lineas.length; i++) {
            String[] t = lineas[i].strip().split("\\s+");
            try {
                switch (t[0]) {
                    case "reset" -> {
                        longitud(t, 1);
                        if (i != 0) throw new IllegalArgumentException("reset must be first");
                        piezas.clear();
                    }
                    case "entry" -> {
                        longitud(t, 4);
                        entrada = pos(t, 1);
                        poner(piezas, entrada, Blocks.AIR.getDefaultState(), madera);
                        poner(piezas, entrada.up(), Blocks.AIR.getDefaultState(), madera);
                        tocadas += 2;
                    }
                    case "omit" -> {
                        longitud(t, 4);
                        quitar(piezas, pos(t, 1));
                        tocadas++;
                    }
                    case "set" -> {
                        longitud(t, 5);
                        poner(piezas, pos(t, 1), estado(t[4]), madera);
                        tocadas++;
                    }
                    case "fill", "shell", "line" -> {
                        longitud(t, 8);
                        BlockState estado = estado(t[7]);
                        if (estado.getBlock() instanceof DoorBlock) {
                            throw new IllegalArgumentException("use set for a door");
                        }
                        tocadas += region(piezas, pos(t, 1), pos(t, 4), estado, madera, t[0]);
                    }
                    case "ellipse", "arch" -> {
                        longitud(t, 8);
                        BlockState estado = estado(t[7]);
                        if (estado.getBlock() instanceof DoorBlock) {
                            throw new IllegalArgumentException("use set for a door");
                        }
                        tocadas += ovalo(piezas, pos(t, 1), pos(t, 4), estado, madera, t[0].equals("arch"));
                    }
                    case "replace" -> {
                        longitud(t, 9);
                        tocadas += sustituir(piezas, pos(t, 1), pos(t, 4), estado(t[7]), estado(t[8]), madera);
                    }
                    default -> throw new IllegalArgumentException("unknown operation");
                }
                if (tocadas > MAX_CELDAS_EDITADAS || piezas.size() > Plano.MAX_PIEZAS) {
                    throw new IllegalArgumentException("block edit exceeds the cell budget");
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("operation " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        validarApoyos(piezas, entrada);
        return Plano.crear(ID, base.revision + 1, "construcción a medida", "custom build",
                base.centro, entrada, new ArrayList<>(piezas.values()));
    }

    private static void longitud(String[] tokens, int esperados) {
        if (tokens.length != esperados) {
            throw new IllegalArgumentException("wrong number of arguments");
        }
    }

    private static BlockPos pos(String[] t, int desde) {
        int x = Integer.parseInt(t[desde]);
        int y = Integer.parseInt(t[desde + 1]);
        int z = Integer.parseInt(t[desde + 2]);
        if (x < -Plano.MAX_EXTENSION || x > Plano.MAX_EXTENSION || y < 0 || y >= Plano.MAX_EXTENSION
                || z < -Plano.MAX_EXTENSION || z > Plano.MAX_EXTENSION) {
            throw new IllegalArgumentException("position outside the blueprint");
        }
        return new BlockPos(x, y, z);
    }

    private static BlockState estado(String spec) {
        int corte = spec.indexOf('[');
        String nombre = corte < 0 ? spec : spec.substring(0, corte);
        Identifier id = Identifier.tryParse(nombre);
        if (id == null || !id.getNamespace().equals("minecraft") || !Registries.BLOCK.containsId(id)) {
            throw new IllegalArgumentException("unknown or unsupported block ID");
        }
        BlockState state = Registries.BLOCK.get(id).getDefaultState();
        if (corte < 0) return state;
        if (!spec.endsWith("]")) throw new IllegalArgumentException("unclosed block properties");
        Set<String> puestos = new HashSet<>();
        for (String property : spec.substring(corte + 1, spec.length() - 1).split(",", -1)) {
            String[] pair = property.split("=", -1);
            if (pair.length != 2 || !Set.of("axis", "facing", "half", "hinge", "type").contains(pair[0])
                    || !puestos.add(pair[0])) {
                throw new IllegalArgumentException("unsupported or repeated property");
            }
            state = PlanoCodec.propiedad(state, pair[0], pair[1]);
        }
        return state;
    }

    private static int region(Map<BlockPos, Plano.Pieza> piezas, BlockPos a, BlockPos b, BlockState estado, Plano.Madera madera, String forma) {
        int dx = Math.abs(b.getX() - a.getX());
        int dy = Math.abs(b.getY() - a.getY());
        int dz = Math.abs(b.getZ() - a.getZ());
        if (forma.equals("line")) {
            int pasos = Math.max(dx, Math.max(dy, dz));
            for (int i = 0; i <= pasos; i++) {
                double t = i / (double) Math.max(1, pasos);
                BlockPos at = new BlockPos(entre(a.getX(), b.getX(), t), entre(a.getY(), b.getY(), t), entre(a.getZ(), b.getZ(), t));
                poner(piezas, at, estado, madera);
            }
            return pasos + 1;
        }
        int volumen = (dx + 1) * (dy + 1) * (dz + 1);
        if (volumen > Plano.MAX_PIEZAS) throw new IllegalArgumentException("region is too large");
        int x0 = Math.min(a.getX(), b.getX());
        int y0 = Math.min(a.getY(), b.getY());
        int z0 = Math.min(a.getZ(), b.getZ());
        boolean shell = forma.equals("shell");
        for (int y = 0; y <= dy; y++) {
            for (int z = 0; z <= dz; z++) {
                for (int x = 0; x <= dx; x++) {
                    if (!shell || x == 0 || x == dx || y == 0 || y == dy || z == 0 || z == dz) {
                        poner(piezas, new BlockPos(x0 + x, y0 + y, z0 + z), estado, madera);
                    }
                }
            }
        }
        return volumen;
    }

    private static int entre(int a, int b, double t) {
        return (int) Math.round(a + (b - a) * t);
    }

    private static int ovalo(Map<BlockPos, Plano.Pieza> piezas, BlockPos a, BlockPos b, BlockState estado, Plano.Madera madera,
                             boolean arco) {
        int dx = Math.abs(a.getX() - b.getX());
        int dy = Math.abs(a.getY() - b.getY());
        int dz = Math.abs(a.getZ() - b.getZ());
        if ((dx == 0 ? 1 : 0) + (dy == 0 ? 1 : 0) + (dz == 0 ? 1 : 0) != 1 || arco && dy == 0) {
            throw new IllegalArgumentException(arco ? "the arch needs a vertical plane" : "the ellipse needs one plane");
        }
        Direction ejeU = dx == 0 ? Direction.SOUTH : Direction.EAST;
        Direction ejeV = dy == 0 ? Direction.SOUTH : Direction.UP;
        int ancho = dx == 0 ? dz : dx;
        int alto = dy == 0 ? dz : dy;
        if (ancho < 2 || alto < 2 || (ancho + 1) * (alto + 1) > Plano.MAX_PIEZAS) {
            throw new IllegalArgumentException("the oval needs at least 3 by 3 cells within the cell budget");
        }
        BlockPos origen = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        for (int u = 0; u <= ancho; u++) {
            BlockPos columna = origen.offset(ejeU, u);
            if (arco) {
                int cima = corona(u, ancho, alto);
                int anterior = corona(Math.max(0, u - 1), ancho, alto);
                int siguiente = corona(Math.min(ancho, u + 1), ancho, alto);
                for (int v = Math.min(cima, Math.min(anterior, siguiente)); v <= cima; v++) {
                    poner(piezas, columna.offset(ejeV, v), estado, madera);
                }
            } else {
                double x = 2.0 * u / ancho - 1.0;
                for (int v = 0; v <= alto; v++) {
                    double y = 2.0 * v / alto - 1.0;
                    if (x * x + y * y <= 1.0) poner(piezas, columna.offset(ejeV, v), estado, madera);
                }
            }
        }
        return (ancho + 1) * (alto + 1);
    }

    private static int corona(int u, int ancho, int alto) {
        double x = 2.0 * u / ancho - 1.0;
        return (int) Math.round(alto * Math.sqrt(1.0 - x * x));
    }

    private static int sustituir(Map<BlockPos, Plano.Pieza> piezas, BlockPos a, BlockPos b, BlockState viejo, BlockState nuevo,
                                 Plano.Madera madera) {
        if (viejo.getBlock() instanceof DoorBlock || nuevo.getBlock() instanceof DoorBlock) {
            throw new IllegalArgumentException("edit the lower door half with set or omit");
        }
        int volumen = (Math.abs(a.getX() - b.getX()) + 1) * (Math.abs(a.getY() - b.getY()) + 1) * (Math.abs(a.getZ() - b.getZ()) + 1);
        if (volumen > Plano.MAX_PIEZAS) throw new IllegalArgumentException("region is too large");
        rol(nuevo, madera);
        int sustituidas = 0;
        for (BlockPos at : BlockPos.iterate(a, b)) {
            Plano.Pieza anterior = piezas.get(at);
            if (anterior != null && anterior.state().equals(viejo)) {
                poner(piezas, at.toImmutable(), nuevo, madera);
                sustituidas++;
            }
        }
        if (sustituidas == 0) throw new IllegalArgumentException("no matching block state in the selected region");
        return volumen;
    }

    private static void quitar(Map<BlockPos, Plano.Pieza> piezas, BlockPos at) {
        Plano.Pieza anterior = piezas.remove(at);
        if (anterior != null && anterior.state().getBlock() instanceof DoorBlock) {
            piezas.remove(anterior.rol() == Plano.Rol.PUERTA ? at.up() : at.down());
        }
    }

    private static void poner(Map<BlockPos, Plano.Pieza> piezas, BlockPos at, BlockState estado, Plano.Madera madera) {
        Plano.Rol rol = rol(estado, madera);
        quitar(piezas, at);
        if (rol == Plano.Rol.PUERTA) {
            if (estado.get(DoorBlock.HALF) != DoubleBlockHalf.LOWER) throw new IllegalArgumentException("set the lower door half");
            quitar(piezas, at.up());
            piezas.put(at.up(), new Plano.Pieza(at.up(), estado.with(DoorBlock.HALF, DoubleBlockHalf.UPPER), Plano.Rol.PUERTA_ARRIBA));
        }
        piezas.put(at, new Plano.Pieza(at, estado, rol));
    }

    private static Plano.Rol rol(BlockState s, Plano.Madera madera) {
        Block b = s.getBlock();
        if (s.isOf(Blocks.AIR)) return Plano.Rol.AIRE;
        if (s.isOf(Blocks.COBBLESTONE)) return Plano.Rol.BASE;
        if (b == madera.log) return Plano.Rol.TRONCO;
        if (b == madera.planks) return Plano.Rol.TABLA;
        if (b == madera.stairs) return Plano.Rol.ESCALON;
        if (b == madera.door) return Plano.Rol.PUERTA;
        if (s.isOf(Blocks.GLASS_PANE)) return Plano.Rol.VIDRIO;
        if (s.isOf(Blocks.TORCH) || s.isOf(Blocks.WALL_TORCH)) return Plano.Rol.ANTORCHA;
        if (s.isOf(Blocks.CRAFTING_TABLE)) return Plano.Rol.MESA;
        if (s.isOf(Blocks.CHEST)) return Plano.Rol.COFRE;
        if (material(s)) return Plano.Rol.MATERIAL;
        throw new IllegalArgumentException("this block cannot be built in a custom plan");
    }

    /** Decorative blocks with a real item and no block entity, gravity, fluid, or powered state. */
    static boolean material(BlockState s) {
        Block b = s.getBlock();
        Identifier id = Registries.BLOCK.getId(b);
        if (!id.getNamespace().equals("minecraft") || !s.getFluidState().isEmpty()) return false;
        String name = id.getPath();
        if (b instanceof SlabBlock) return s.get(SlabBlock.TYPE) != SlabType.DOUBLE;
        if (b instanceof StairsBlock) return true;
        if (Plano.Madera.de(b) != null) return s.contains(Properties.AXIS) || b.getClass() == Block.class;
        return s.getProperties().isEmpty() && (Plano.piedra(s) || s.isOf(Blocks.BRICKS) || s.isOf(Blocks.GLASS)
                || s.isOf(Blocks.TERRACOTTA) || name.endsWith("_wool") || name.endsWith("_concrete")
                || name.endsWith("_terracotta") || name.endsWith("_stained_glass")
                || name.endsWith("_bricks") || name.endsWith("_tiles"));
    }

    private static void validarApoyos(Map<BlockPos, Plano.Pieza> piezas, BlockPos entrada) {
        Set<BlockPos> solidos = new HashSet<>();
        for (Plano.Pieza p : piezas.values()) {
            if (!p.state().isAir()) solidos.add(p.rel());
        }
        if (solidos.isEmpty() || !solidos.contains(entrada.down())) {
            throw new IllegalArgumentException("the entrance needs a floor under it");
        }
        ArrayDeque<BlockPos> frente = new ArrayDeque<>();
        solidos.removeIf(p -> {
            if (p.getY() != 0) return false;
            frente.add(p);
            return true;
        });
        while (!frente.isEmpty()) {
            BlockPos p = frente.removeFirst();
            for (Direction d : Direction.values()) {
                BlockPos vecino = p.offset(d);
                if (solidos.remove(vecino)) frente.addLast(vecino);
            }
        }
        if (!solidos.isEmpty()) throw new IllegalArgumentException("some blocks float away from the foundation");
    }

    /** Exact compact horizontal rows. Lower-case keys label states, '-' is explicit air, '.' is unspecified. */
    public static String describir(Plano plano, Plano.Madera madera) {
        StringBuilder out = new StringBuilder("Draft ").append(plano.id).append(" rev ").append(plano.revision)
                .append(". Center ").append(plano.centro.toShortString()).append(", entry ").append(plano.entrada.toShortString())
                .append(", main wood ").append(Registries.BLOCK.getId(madera.planks))
                .append(". X ").append(plano.minX).append("..").append(plano.maxX)
                .append(", Z ").append(plano.minZ).append("..").append(plano.maxZ)
                .append(", Y 0..").append(plano.alto - 1).append(". ");
        Map<BlockPos, Plano.Pieza> celdas = new LinkedHashMap<>();
        Map<BlockState, Character> paleta = new LinkedHashMap<>();
        String letras = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        for (Plano.Pieza p : plano.piezas) {
            celdas.put(p.rel(), p);
            if (!p.state().isAir() && !paleta.containsKey(p.state()) && paleta.size() < letras.length()) {
                paleta.put(p.state(), letras.charAt(paleta.size()));
            }
        }
        out.append("Palette (keys in the rows): ");
        paleta.forEach((s, key) -> out.append(key).append('=').append(s.toString().replace("Block{", "").replace("}", "")).append(' '));
        out.append("\nRows use - for air, . for unspecified and ? for unlisted state. In each y layer, rows go from z=")
                .append(plano.minZ).append(" north to south, cells from x=").append(plano.minX).append(" west to east.\n");
        for (int y = 0; y < plano.alto; y++) {
            int area = (plano.maxX - plano.minX + 2) * (plano.maxZ - plano.minZ + 1);
            if (out.length() + area > 5000) {
                out.append("Layers starting at y=").append(y).append(" omitted from this compact view.");
                break;
            }
            out.append("y=").append(y).append(' ');
            for (int z = plano.minZ; z <= plano.maxZ; z++) {
                for (int x = plano.minX; x <= plano.maxX; x++) {
                    Plano.Pieza p = celdas.get(new BlockPos(x, y, z));
                    out.append(p == null ? '.' : p.state().isAir() ? '-' : paleta.getOrDefault(p.state(), '?'));
                }
                out.append('/');
            }
            out.append('\n');
        }
        return out.toString();
    }
}
