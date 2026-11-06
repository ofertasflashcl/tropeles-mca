package net.mca.entity.ai.obra;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.StairShape;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The houses besides the first one, drawn block by block, and what any plan is checked against before a villager
 * offers it. Two designs of its own: a one-storey cabin of laid logs with a covered porch, and a long house with two
 * rooms in a row under a low gable. The first house ({@link Plano#casa}) is kept exactly as it is and only read here.
 * Every design comes in any wood (the wood is what it is built of, not another design), takes the roofs its walls can
 * carry ({@link Cubierta}) and lets a draft open or wall up windows in its outer walls. Pure data: no world, no network.
 */
public final class ModelosCasa {
    /** Tallest a house may stand, footing included: past this its roof is out of a builder's reach. */
    public static final int ALTO_MAX = 12;
    /** Widest a roof of stairs may span, wall to wall. */
    public static final int LUZ_MAX = 9;
    /** What any plan keeps to (the plan's own limits). */
    public static final int CELDAS_MAX = 4096;
    public static final int EJE_MAX = 64;

    /** The roofs a design can be given: its own, a steeper one, or its own with the ridge turned. */
    public enum Cubierta {
        /** Its own: a row of stairs one up for every one in, to a ridge of planks. */
        DOS_AGUAS("techo a dos aguas", "gable roof"),
        /** Twice as steep: a plank under every row of stairs. */
        ALTO("techo alto", "steep roof"),
        /** Its own pitch, with the ridge running the other way (the gables move to the other two walls). */
        TRAVES("techo girado", "turned roof");

        public final String es;
        public final String en;

        Cubierta(String es, String en) {
            this.es = es;
            this.en = en;
        }
    }

    /**
     * A design: its outer walls (the box its roof covers, in plan coordinates: front to the north, -z), the layer its
     * roof starts on, which way its own ridge runs and whether its own roof has eaves past the walls.
     */
    public enum Modelo {
        /** The first house, as {@link Plano#casa} draws it. */
        DOS_PISOS(null, "", "", "dos pisos, con escalera adentro y el frontón al frente", "two storeys, a staircase inside and the gable over the front",
                8, 0, 6, 0, 6, Direction.Axis.Z, false, new int[0], null, null),
        CABANA("tropeles:casa/cabana", "cabaña de troncos", "log cabin", "un piso de troncos acostados, con porche techado y techo a dos aguas",
                "one storey of laid logs, a covered porch and a gable roof", 4, 0, 6, 0, 4, Direction.Axis.X, true, new int[0],
                new BlockPos(3, 0, 2), new BlockPos(3, 1, -1)),
        LARGA("tropeles:casa/larga", "casa larga", "long house", "un piso con dos cuartos en fila, aleros a los costados y el frontón al frente",
                "one storey with two rooms in a row, eaves along the sides and the gable over the front", 4, 0, 4, 0, 10, Direction.Axis.Z, true,
                new int[]{5}, new BlockPos(2, 0, 5), new BlockPos(2, 1, -1));

        @Nullable
        private final String id;
        private final String nombre;
        private final String nombreEn;
        public final String descripcion;
        public final String descripcionEn;
        /** The first layer of the roof: the walls stop under it. */
        public final int techo;
        public final int x0;
        public final int x1;
        public final int z0;
        public final int z1;
        /** Which way its own ridge runs. */
        public final Direction.Axis cumbrera;
        /** Its own roof reaches one row past the walls on both slopes. */
        public final boolean aleros;
        /** Inner walls that rise to the roof, by where they stand along the ridge. */
        private final int[] tabiques;
        @Nullable
        private final BlockPos centro;
        @Nullable
        private final BlockPos entrada;

        Modelo(@Nullable String id, String nombre, String nombreEn, String descripcion, String descripcionEn, int techo, int x0, int x1, int z0,
               int z1, Direction.Axis cumbrera, boolean aleros, int[] tabiques, @Nullable BlockPos centro, @Nullable BlockPos entrada) {
            this.id = id;
            this.nombre = nombre;
            this.nombreEn = nombreEn;
            this.descripcion = descripcion;
            this.descripcionEn = descripcionEn;
            this.techo = techo;
            this.x0 = x0;
            this.x1 = x1;
            this.z0 = z0;
            this.z1 = z1;
            this.cumbrera = cumbrera;
            this.aleros = aleros;
            this.tabiques = tabiques;
            this.centro = centro;
            this.entrada = entrada;
        }

        private static Plano primera() {
            return Plano.casa(Plano.Madera.ROBLE);
        }

        public String id() {
            return id != null ? id : primera().id;
        }

        public int revision() {
            return id != null ? 1 : primera().revision;
        }

        public String nombre(boolean es) {
            if (id == null) {
                return es ? primera().nombre : primera().nombreEn;
            }
            return es ? nombre : nombreEn;
        }

        public BlockPos centro() {
            return centro != null ? centro : primera().centro;
        }

        public BlockPos entrada() {
            return entrada != null ? entrada : primera().entrada;
        }

        @Nullable
        public static Modelo de(String id) {
            for (Modelo m : values()) {
                if (m.id().equals(id)) {
                    return m;
                }
            }
            return null;
        }
    }

    private ModelosCasa() {
    }

    // ------------------------------------------------------------------ drawing a plan

    /**
     * The plan of {@code modelo} in {@code madera} under {@code cubierta}, with the windows at {@code abiertas} opened
     * and those at {@code cerradas} walled up (plan coordinates), as revision {@code revision}. The first house with
     * nothing changed is {@link Plano#casa} itself. Null when that roof does not suit those walls, with why in
     * {@code porQue} (Spanish, English).
     */
    @Nullable
    static Plano generar(Modelo modelo, Plano.Madera madera, Cubierta cubierta, Collection<BlockPos> abiertas, Collection<BlockPos> cerradas,
                         int revision, String[] porQue) {
        return generar(modelo, madera, cubierta, abiertas, cerradas, revision, null, porQue);
    }

    @Nullable
    static Plano generar(Modelo modelo, Plano.Madera madera, Cubierta cubierta, Collection<BlockPos> abiertas, Collection<BlockPos> cerradas,
                         int revision, @Nullable CambioCasa.Lado balcon, String[] porQue) {
        if (!admite(modelo, cubierta, porQue)) {
            return null;
        }
        if (balcon != null && modelo != Modelo.DOS_PISOS) {
            porQue[0] = "para llegar a un balcón necesito el segundo piso. Pídeme la casa de dos pisos";
            porQue[1] = "a balcony needs access from upstairs. Ask me for the two-storey house";
            return null;
        }
        if (balcon == null && modelo == Modelo.DOS_PISOS && cubierta == Cubierta.DOS_AGUAS && abiertas.isEmpty()
                && cerradas.isEmpty() && revision == modelo.revision()) {
            return Plano.casa(madera);
        }
        Dibujo d = new Dibujo();
        switch (modelo) {
            case DOS_PISOS -> primera(d, madera, cubierta == Cubierta.DOS_AGUAS, abiertas, cerradas);
            case CABANA -> cabana(d, madera, ventanas(modelo, abiertas, cerradas));
            case LARGA -> larga(d, madera, ventanas(modelo, abiertas, cerradas));
        }
        if (modelo != Modelo.DOS_PISOS || cubierta != Cubierta.DOS_AGUAS) {
            techo(d, madera, modelo, cubierta);
        }
        String es = modelo.nombre(true) + (cubierta == Cubierta.DOS_AGUAS ? "" : ", " + cubierta.es);
        String en = modelo.nombre(false) + (cubierta == Cubierta.DOS_AGUAS ? "" : ", " + cubierta.en);
        if (balcon != null) {
            balcon(d, madera, balcon);
            es += " con balcón " + balcon.es;
            en += " with a balcony " + balcon.en;
        }
        return Plano.crear(modelo.id(), revision, es, en, modelo.centro(), modelo.entrada(), d.lista());
    }

    /** A walkable deck upstairs, with a two-block opening, a parapet and posts down to ground level. */
    private static void balcon(Dibujo d, Plano.Madera madera, CambioCasa.Lado lado) {
        Direction exterior = switch (lado) {
            case FRENTE -> Direction.NORTH;
            case ATRAS -> Direction.SOUTH;
            case IZQUIERDA -> Direction.EAST;
            case DERECHA -> Direction.WEST;
        };
        // Avoid the torch at the back and the staircase opening along the east wall.
        BlockPos piso = switch (lado) {
            case FRENTE -> new BlockPos(3, 4, 0);
            case ATRAS -> new BlockPos(2, 4, 6);
            case IZQUIERDA -> new BlockPos(6, 4, 1);
            case DERECHA -> new BlockPos(0, 4, 3);
        };
        for (int y = 1; y <= 2; y++) {
            BlockPos at = piso.up(y);
            d.p.put(at, new Plano.Pieza(at, Blocks.AIR.getDefaultState(), Plano.Rol.AIRE));
        }
        Direction costado = exterior.rotateYClockwise();
        BlockState tabla = madera.planks.getDefaultState();
        for (int profundidad = 1; profundidad <= 3; profundidad++) {
            for (int ancho = -1; ancho <= 1; ancho++) {
                BlockPos at = piso.offset(exterior, profundidad).offset(costado, ancho);
                d.put(at, tabla, Plano.Rol.TABLA);
                if (ancho != 0 || profundidad == 3) {
                    d.put(at.up(), tabla, Plano.Rol.TABLA);
                } else {
                    d.put(at.up(), Blocks.AIR.getDefaultState(), Plano.Rol.AIRE);
                    d.put(at.up(2), Blocks.AIR.getDefaultState(), Plano.Rol.AIRE);
                }
                if (ancho != 0 && profundidad == 3) {
                    for (int y = 1; y <= 4; y++) {
                        d.put(at.down(y), tronco(madera, Direction.Axis.Y), Plano.Rol.TRONCO);
                    }
                }
            }
        }
    }

    /** The first house drawn again, its own roof made with {@link #techo}: the check that the roof module draws what {@link Plano#casa} draws. */
    public static Plano primeraRehecha(Plano.Madera madera) {
        Dibujo d = new Dibujo();
        primera(d, madera, false, List.of(), List.of());
        techo(d, madera, Modelo.DOS_PISOS, Cubierta.DOS_AGUAS);
        Plano casa = Plano.casa(madera);
        return Plano.crear(casa.id, casa.revision, casa.nombre, casa.nombreEn, casa.centro, casa.entrada, d.lista());
    }

    /** Whether {@code cubierta} suits the walls of {@code modelo}: a span it can bridge, a height it can be built to. */
    public static boolean admite(Modelo modelo, Cubierta cubierta, String[] porQue) {
        Direction.Axis cumbrera = cubierta == Cubierta.TRAVES ? otro(modelo.cumbrera) : modelo.cumbrera;
        int luz = cumbrera == Direction.Axis.Z ? modelo.x1 - modelo.x0 + 1 : modelo.z1 - modelo.z0 + 1;
        if (luz > LUZ_MAX) {
            porQue[0] = "ese techo cruzaría " + luz + " bloques de muro a muro, y más de " + LUZ_MAX + " no se sostiene";
            porQue[1] = "that roof would span " + luz + " blocks wall to wall, and more than " + LUZ_MAX + " won't hold";
            return false;
        }
        if (luz % 2 == 0) {
            porQue[0] = "ese techo cruzaría " + luz + " bloques, un número par: no le queda cumbrera";
            porQue[1] = "that roof would span " + luz + " blocks, an even number: it would have no ridge";
            return false;
        }
        int mitad = (luz - 1) / 2;
        int alto = modelo.techo + (cubierta == Cubierta.ALTO ? 2 * mitad : mitad);
        if (alto > ALTO_MAX) {
            porQue[0] = "con ese techo la casa tendría " + alto + " bloques de alto: muy alta para techarla";
            porQue[1] = "with that roof the house would stand " + alto + " blocks tall: too high to roof";
            return false;
        }
        return true;
    }

    private static Direction.Axis otro(Direction.Axis a) {
        return a == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
    }

    /** The windows this design is drawn with (plan coordinates). */
    public static Set<BlockPos> ventanasDe(Modelo modelo) {
        Set<BlockPos> out = new LinkedHashSet<>();
        switch (modelo) {
            case DOS_PISOS -> {
                for (Plano.Pieza p : Plano.casa(Plano.Madera.ROBLE).piezas) {
                    if (p.rol() == Plano.Rol.VIDRIO) {
                        out.add(p.rel());
                    }
                }
            }
            case CABANA -> {
                for (int[] w : new int[][]{{1, 2, 0}, {5, 2, 0}, {0, 2, 2}, {6, 2, 2}, {2, 2, 4}, {4, 2, 4}}) {
                    out.add(new BlockPos(w[0], w[1], w[2]));
                }
            }
            case LARGA -> {
                for (int[] w : new int[][]{{1, 2, 0}, {3, 2, 0}, {0, 2, 2}, {0, 2, 3}, {0, 2, 7}, {0, 2, 8}, {4, 2, 2}, {4, 2, 3}, {4, 2, 7}, {4, 2, 8},
                        {2, 2, 10}}) {
                    out.add(new BlockPos(w[0], w[1], w[2]));
                }
            }
        }
        return out;
    }

    private static Set<BlockPos> ventanas(Modelo modelo, Collection<BlockPos> abiertas, Collection<BlockPos> cerradas) {
        Set<BlockPos> out = ventanasDe(modelo);
        out.removeAll(cerradas);
        out.addAll(abiertas);
        return out;
    }

    /**
     * Whether a window may be opened at {@code p} of {@code plano} (plan coordinates): a plank or laid log of the
     * outer walls between the floor and the roof, not a corner, post or door, with an empty room right behind it.
     */
    static boolean pared(Modelo modelo, Plano plano, BlockPos p) {
        Plano.Pieza q = null;
        Plano.Pieza dentro = null;
        BlockPos adentro = null;
        boolean bx = p.getX() == modelo.x0 || p.getX() == modelo.x1;
        boolean bz = p.getZ() == modelo.z0 || p.getZ() == modelo.z1;
        boolean caja = p.getX() >= modelo.x0 && p.getX() <= modelo.x1 && p.getZ() >= modelo.z0 && p.getZ() <= modelo.z1;
        if (!caja || bx == bz || p.getY() < 1 || p.getY() >= modelo.techo) {
            return false; // off the walls, inside the house, or a corner
        }
        adentro = bx ? p.offset(p.getX() == modelo.x0 ? Direction.EAST : Direction.WEST) : p.offset(p.getZ() == modelo.z0 ? Direction.SOUTH : Direction.NORTH);
        for (Plano.Pieza x : plano.piezas) {
            if (x.rel().equals(p)) {
                q = x;
            } else if (x.rel().equals(adentro)) {
                dentro = x;
            }
        }
        if (q == null || dentro == null || dentro.rol() != Plano.Rol.AIRE) {
            return false;
        }
        return q.rol() == Plano.Rol.TABLA
                || q.rol() == Plano.Rol.TRONCO && q.state().contains(Properties.AXIS) && q.state().get(Properties.AXIS).isHorizontal();
    }

    /** The first house's blocks: all of them, or only those under its roof; the windows changed as asked. */
    private static void primera(Dibujo d, Plano.Madera m, boolean conTecho, Collection<BlockPos> abiertas, Collection<BlockPos> cerradas) {
        BlockState pane = Blocks.GLASS_PANE.getDefaultState();
        BlockState planks = m.planks.getDefaultState();
        for (Plano.Pieza p : Plano.casa(m).piezas) {
            if (!conTecho && p.rel().getY() >= Modelo.DOS_PISOS.techo) {
                continue;
            }
            if (abiertas.contains(p.rel())) {
                d.put(p.rel(), pane, Plano.Rol.VIDRIO);
            } else if (cerradas.contains(p.rel())) {
                d.put(p.rel(), planks, Plano.Rol.TABLA);
            } else {
                d.put(p.rel(), p.state(), p.rol());
            }
        }
    }

    /**
     * A cabin of laid logs, 7 by 5: a stone footing with a plank floor, three courses of logs laid along each wall
     * with upright logs at the corners, a door in the middle of the front, six windows, a torch inside and two on the
     * porch, a crafting table and a chest by the back wall. A porch deck runs along the front under a canopy of stairs
     * on two log posts. One room, open up to the roof.
     */
    private static void cabana(Dibujo d, Plano.Madera m, Set<BlockPos> ventanas) {
        BlockState stone = Blocks.COBBLESTONE.getDefaultState();
        BlockState planks = m.planks.getDefaultState();
        // the door, windows, torches and furniture first: the walls never take their cells
        puerta(d, m, 3, 0);
        for (BlockPos w : ventanas) {
            d.put(w, Blocks.GLASS_PANE.getDefaultState(), Plano.Rol.VIDRIO);
        }
        antorcha(d, 3, 2, 3, Direction.NORTH);
        antorcha(d, 2, 2, -1, Direction.NORTH);
        antorcha(d, 4, 2, -1, Direction.NORTH);
        d.put(new BlockPos(1, 1, 3), Blocks.CRAFTING_TABLE.getDefaultState(), Plano.Rol.MESA);
        d.put(new BlockPos(5, 1, 3), Blocks.CHEST.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH), Plano.Rol.COFRE);
        // the footing, and the porch deck in front with stone under its posts
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 4; z++) {
                boolean ring = x == 0 || x == 6 || z == 0 || z == 4;
                d.put(new BlockPos(x, 0, z), ring ? stone : planks, ring ? Plano.Rol.BASE : Plano.Rol.TABLA);
            }
            boolean post = x == 0 || x == 6;
            d.put(new BlockPos(x, 0, -1), post ? stone : planks, post ? Plano.Rol.BASE : Plano.Rol.TABLA);
        }
        // the walls: logs laid along each wall, upright at the corners
        for (int y = 1; y <= 3; y++) {
            for (int x = 0; x <= 6; x++) {
                for (int z = 0; z <= 4; z++) {
                    boolean bx = x == 0 || x == 6;
                    boolean bz = z == 0 || z == 4;
                    if (!bx && !bz) {
                        continue;
                    }
                    Direction.Axis axis = bx && bz ? Direction.Axis.Y : bz ? Direction.Axis.X : Direction.Axis.Z;
                    d.put(new BlockPos(x, y, z), tronco(m, axis), Plano.Rol.TRONCO);
                }
            }
        }
        // the porch: two posts and a canopy of stairs over the deck
        for (int y = 1; y <= 2; y++) {
            d.put(new BlockPos(0, y, -1), tronco(m, Direction.Axis.Y), Plano.Rol.TRONCO);
            d.put(new BlockPos(6, y, -1), tronco(m, Direction.Axis.Y), Plano.Rol.TRONCO);
        }
        for (int x = 0; x <= 6; x++) {
            d.put(new BlockPos(x, 3, -1), escalon(m, Direction.SOUTH), Plano.Rol.ESCALON);
        }
        // the room and the way along the porch stay empty (the roof adds the space under it)
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 3; z++) {
                for (int y = 1; y <= 3; y++) {
                    d.aire(x, y, z);
                }
            }
            d.aire(x, 1, -1);
            d.aire(x, 2, -1);
        }
    }

    /**
     * A long house, 5 by 11: a stone footing with a plank floor, plank walls three high on log corners, with log posts
     * where the inner wall meets the sides; a front room and a back room in a row, an open doorway between them; the
     * door in the middle of the front with a stone step and a torch over it; eleven windows; a torch in each room, a
     * crafting table in the front one and a chest at the back.
     */
    private static void larga(Dibujo d, Plano.Madera m, Set<BlockPos> ventanas) {
        BlockState stone = Blocks.COBBLESTONE.getDefaultState();
        BlockState planks = m.planks.getDefaultState();
        puerta(d, m, 2, 0);
        for (BlockPos w : ventanas) {
            d.put(w, Blocks.GLASS_PANE.getDefaultState(), Plano.Rol.VIDRIO);
        }
        antorcha(d, 3, 2, 4, Direction.WEST);
        antorcha(d, 1, 2, 6, Direction.EAST);
        antorcha(d, 2, 3, -1, Direction.NORTH);
        d.put(new BlockPos(1, 1, 4), Blocks.CRAFTING_TABLE.getDefaultState(), Plano.Rol.MESA);
        d.put(new BlockPos(2, 1, 9), Blocks.CHEST.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH), Plano.Rol.COFRE);
        // the doorway between the rooms, before the inner wall takes its cells
        d.aire(2, 1, 5);
        d.aire(2, 2, 5);
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 10; z++) {
                boolean ring = x == 0 || x == 4 || z == 0 || z == 10;
                d.put(new BlockPos(x, 0, z), ring ? stone : planks, ring ? Plano.Rol.BASE : Plano.Rol.TABLA);
            }
        }
        d.put(new BlockPos(2, 0, -1), stone, Plano.Rol.BASE);
        for (int y = 1; y <= 3; y++) {
            for (int x = 0; x <= 4; x++) {
                for (int z = 0; z <= 10; z++) {
                    boolean bx = x == 0 || x == 4;
                    boolean bz = z == 0 || z == 10;
                    if (bx || bz) {
                        boolean log = bx && (bz || z == 5);
                        d.put(new BlockPos(x, y, z), log ? tronco(m, Direction.Axis.Y) : planks, log ? Plano.Rol.TRONCO : Plano.Rol.TABLA);
                    } else if (z == 5) {
                        d.put(new BlockPos(x, y, z), planks, Plano.Rol.TABLA);
                    }
                }
            }
        }
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 9; z++) {
                for (int y = 1; y <= 3; y++) {
                    d.aire(x, y, z);
                }
            }
        }
        d.aire(2, 1, -1);
        d.aire(2, 2, -1);
    }

    /**
     * The roof over the walls of {@code modelo}: rows of stairs climbing from both sides to a ridge of planks (a plank
     * under every row when steep), gables of planks closing both ends and the inner walls that rise to it, the attic
     * left empty, and a row of eaves past the walls when its own roof has them.
     */
    private static void techo(Dibujo d, Plano.Madera m, Modelo mo, Cubierta c) {
        Direction.Axis cumbrera = c == Cubierta.TRAVES ? otro(mo.cumbrera) : mo.cumbrera;
        boolean alto = c == Cubierta.ALTO;
        boolean aleros = mo.aleros && !alto;
        boolean enX = cumbrera == Direction.Axis.Z; // the slopes run across x
        int a0 = enX ? mo.x0 : mo.z0;
        int a1 = enX ? mo.x1 : mo.z1;
        int b0 = enX ? mo.z0 : mo.x0;
        int b1 = enX ? mo.z1 : mo.x1;
        Direction sube = enX ? Direction.EAST : Direction.SOUTH; // the slope from a0 climbs this way
        int mitad = (a1 - a0) / 2;
        int y0 = mo.techo;
        BlockState planks = m.planks.getDefaultState();
        Set<Integer> tabiques = new HashSet<>();
        for (int t : mo.tabiques) {
            tabiques.add(t);
        }
        for (int i = 0; i < mitad; i++) {
            for (int b = b0; b <= b1; b++) {
                for (int lado = 0; lado < 2; lado++) {
                    int a = lado == 0 ? a0 + i : a1 - i;
                    Direction f = lado == 0 ? sube : sube.getOpposite();
                    if (alto) {
                        d.put(celda(enX, a, y0 + 2 * i, b), planks, Plano.Rol.TABLA);
                        d.put(celda(enX, a, y0 + 2 * i + 1, b), escalon(m, f), Plano.Rol.ESCALON);
                    } else {
                        d.put(celda(enX, a, y0 + i, b), escalon(m, f), Plano.Rol.ESCALON);
                    }
                }
            }
        }
        for (int b = b0; b <= b1; b++) {
            if (alto) {
                d.put(celda(enX, a0 + mitad, y0 + 2 * mitad - 2, b), planks, Plano.Rol.TABLA);
                d.put(celda(enX, a0 + mitad, y0 + 2 * mitad - 1, b), planks, Plano.Rol.TABLA);
            } else {
                d.put(celda(enX, a0 + mitad, y0 + mitad - 1, b), planks, Plano.Rol.TABLA);
            }
        }
        // under the roof: planks where a wall rises to it, empty attic between them
        for (int a = a0 + 1; a < a1; a++) {
            int i = Math.min(a - a0, a1 - a);
            int bajo = y0 + (i == mitad ? (alto ? 2 * mitad - 2 : mitad - 1) : (alto ? 2 * i : i));
            for (int y = y0; y < bajo; y++) {
                for (int b = b0; b <= b1; b++) {
                    if (b == b0 || b == b1 || tabiques.contains(b)) {
                        d.put(celda(enX, a, y, b), planks, Plano.Rol.TABLA);
                    }
                }
            }
            for (int y = y0; y < bajo; y++) {
                for (int b = b0 + 1; b < b1; b++) {
                    if (!tabiques.contains(b)) {
                        d.put(celda(enX, a, y, b), Blocks.AIR.getDefaultState(), Plano.Rol.AIRE);
                    }
                }
            }
        }
        if (aleros) {
            for (int b = b0; b <= b1; b++) {
                d.put(celda(enX, a0 - 1, y0 - 1, b), escalon(m, sube), Plano.Rol.ESCALON);
                d.put(celda(enX, a1 + 1, y0 - 1, b), escalon(m, sube.getOpposite()), Plano.Rol.ESCALON);
            }
        }
    }

    private static BlockPos celda(boolean enX, int a, int y, int b) {
        return enX ? new BlockPos(a, y, b) : new BlockPos(b, y, a);
    }

    private static void puerta(Dibujo d, Plano.Madera m, int x, int z) {
        BlockState door = m.door.getDefaultState().with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HINGE, DoorHinge.LEFT)
                .with(DoorBlock.OPEN, false).with(DoorBlock.POWERED, false);
        d.put(new BlockPos(x, 1, z), door.with(DoorBlock.HALF, DoubleBlockHalf.LOWER), Plano.Rol.PUERTA);
        d.put(new BlockPos(x, 2, z), door.with(DoorBlock.HALF, DoubleBlockHalf.UPPER), Plano.Rol.PUERTA_ARRIBA);
    }

    /** A torch on the wall behind it: {@code mira} is the way it points, away from its wall. */
    private static void antorcha(Dibujo d, int x, int y, int z, Direction mira) {
        d.put(new BlockPos(x, y, z), Blocks.WALL_TORCH.getDefaultState().with(Properties.HORIZONTAL_FACING, mira), Plano.Rol.ANTORCHA);
    }

    private static BlockState tronco(Plano.Madera m, Direction.Axis axis) {
        return m.log.getDefaultState().with(Properties.AXIS, axis);
    }

    /** A stair whose high side (the way one climbs it) is {@code f}. */
    private static BlockState escalon(Plano.Madera m, Direction f) {
        return m.stairs.getDefaultState().with(Properties.HORIZONTAL_FACING, f).with(Properties.BLOCK_HALF, BlockHalf.BOTTOM)
                .with(Properties.STAIR_SHAPE, StairShape.STRAIGHT);
    }

    /** The pieces of a plan being drawn: the first set at a cell stays; blocks bottom up, then the empty cells. */
    private static final class Dibujo {
        private final Map<BlockPos, Plano.Pieza> p = new LinkedHashMap<>();

        void put(BlockPos at, BlockState state, Plano.Rol rol) {
            BlockPos pos = at.toImmutable();
            p.putIfAbsent(pos, new Plano.Pieza(pos, state, rol));
        }

        void aire(int x, int y, int z) {
            put(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), Plano.Rol.AIRE);
        }

        List<Plano.Pieza> lista() {
            for (Map.Entry<BlockPos, Plano.Pieza> e : p.entrySet()) {
                Plano.Pieza w = e.getValue();
                if (w.rol() != Plano.Rol.VIDRIO) {
                    continue;
                }
                BlockState s = w.state();
                for (Direction dir : Direction.Type.HORIZONTAL) {
                    Plano.Pieza n = p.get(e.getKey().offset(dir));
                    s = s.with(lado(dir), n != null && une(n.rol()));
                }
                e.setValue(new Plano.Pieza(w.rel(), s, Plano.Rol.VIDRIO));
            }
            List<Plano.Pieza> list = new ArrayList<>();
            for (Plano.Pieza piece : p.values()) {
                if (piece.rol() != Plano.Rol.AIRE) {
                    list.add(piece);
                }
            }
            list.sort(Comparator.comparingInt(a -> a.rel().getY()));
            for (Plano.Pieza piece : p.values()) {
                if (piece.rol() == Plano.Rol.AIRE) {
                    list.add(piece);
                }
            }
            return list;
        }
    }

    /** What a pane joins in a wall: whole blocks and other panes. */
    private static boolean une(Plano.Rol r) {
        return r == Plano.Rol.BASE || r == Plano.Rol.TRONCO || r == Plano.Rol.TABLA || r == Plano.Rol.VIDRIO || r == Plano.Rol.MESA;
    }

    private static BooleanProperty lado(Direction d) {
        return switch (d) {
            case NORTH -> Properties.NORTH;
            case SOUTH -> Properties.SOUTH;
            case EAST -> Properties.EAST;
            default -> Properties.WEST;
        };
    }

    // ------------------------------------------------------------------ checking a plan

    /** One thing wrong with a plan, in Spanish and English. */
    public record Problema(String es, String en) {
    }

    /**
     * What {@link #revisar} found: the problems (none when the plan will do) and what it measured: cells, blocks, the
     * extent on each axis, the floor cells, how many of them can be walked to from the door, and on how many levels.
     */
    public record Revision(List<Problema> problemas, int celdas, int bloques, int ancho, int fondo, int alto, int suelo, int alcanzado, int pisos) {
        public boolean ok() {
            return problemas.isEmpty();
        }

        public String resumen() {
            return celdas + " celdas, " + bloques + " bloques, " + ancho + "x" + fondo + "x" + alto + ", piso " + alcanzado + "/" + suelo + " en " + pisos
                    + " nivel(es)" + (problemas.isEmpty() ? "" : "; " + problemas.get(0).es());
        }
    }

    /**
     * Checks a plan as a house to build and to live in, turned to face {@code frente} the way a work sets it down
     * (cells turned about the corner, states turned with them): within the limits; one door, its two halves matching,
     * on firm floor, the entrance right in front of it and both sides of it kept clear; every torch on a wall; an order
     * in which a builder could set every block against one already set, layer by layer as it works; and every floor
     * cell reached walking from the entrance (up stairs, never jumping onto a block), with a way out from it.
     */
    public static Revision revisar(Plano plano, Direction frente) {
        BlockRotation giro = Plano.giro(frente);
        Map<BlockPos, Plano.Pieza> c = new HashMap<>();
        for (Plano.Pieza p : plano.piezas) {
            BlockPos at = Plano.enMundo(BlockPos.ORIGIN, giro, p.rel()).toImmutable();
            c.put(at, new Plano.Pieza(at, p.state().rotate(giro), p.rol()));
        }
        return revisar(c, plano.piezas.size(), Plano.enMundo(BlockPos.ORIGIN, giro, plano.entrada).toImmutable());
    }

    private static Revision revisar(Map<BlockPos, Plano.Pieza> c, int n, BlockPos entrada) {
        List<Problema> out = new ArrayList<>();
        if (c.isEmpty()) {
            out.add(new Problema("el plano no tiene piezas", "the plan has no pieces"));
            return new Revision(List.copyOf(out), 0, 0, 0, 0, 0, 0, 0, 0);
        }
        int x0 = Integer.MAX_VALUE;
        int y0 = Integer.MAX_VALUE;
        int z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int y1 = Integer.MIN_VALUE;
        int z1 = Integer.MIN_VALUE;
        int bloques = 0;
        for (Plano.Pieza p : c.values()) {
            BlockPos q = p.rel();
            x0 = Math.min(x0, q.getX());
            y0 = Math.min(y0, q.getY());
            z0 = Math.min(z0, q.getZ());
            x1 = Math.max(x1, q.getX());
            y1 = Math.max(y1, q.getY());
            z1 = Math.max(z1, q.getZ());
            if (p.rol().bloque()) {
                bloques++;
            }
        }
        int ancho = x1 - x0 + 1;
        int fondo = z1 - z0 + 1;
        int alto = y1 - y0 + 1;
        if (c.size() != n) {
            out.add(new Problema("hay dos piezas en una misma celda", "two pieces share a cell"));
        }
        if (n > CELDAS_MAX) {
            out.add(new Problema("tiene " + n + " celdas, más de " + CELDAS_MAX, "it has " + n + " cells, more than " + CELDAS_MAX));
        }
        if (ancho > EJE_MAX || fondo > EJE_MAX || alto > EJE_MAX) {
            out.add(new Problema("mide " + ancho + "x" + fondo + "x" + alto + ", más de " + EJE_MAX + " por lado",
                    "it measures " + ancho + "x" + fondo + "x" + alto + ", more than " + EJE_MAX + " a side"));
        }
        if (y0 < 0) {
            out.add(new Problema("tiene piezas bajo el cimiento", "it has pieces below the footing"));
        }
        puerta(c, entrada, out);
        for (Plano.Pieza p : c.values()) {
            if (p.rol() == Plano.Rol.ANTORCHA && !entero(c.get(apoyo(p)))) {
                out.add(new Problema("la antorcha de " + texto(p.rel()) + " no tiene pared donde ir", "the torch at " + texto(p.rel()) + " has no wall to go on"));
                break;
            }
        }
        BlockPos colgada = sinApoyo(c);
        if (colgada != null) {
            out.add(new Problema("hay bloques que no tendrían contra qué ponerse al construir, como el de " + texto(colgada),
                    "some blocks would have nothing to be set against while building, such as the one at " + texto(colgada)));
        }
        Acceso a = acceso(c, entrada, x0, x1, z0, z1, y1);
        if (!a.desde()) {
            out.add(new Problema("no se puede estar de pie en la entrada", "there is no standing at the entrance"));
        } else {
            if (a.alcanzado() < a.suelo()) {
                out.add(new Problema((a.suelo() - a.alcanzado()) + " celdas del piso no se alcanzan caminando desde la puerta, como la de " + texto(a.primera()),
                        (a.suelo() - a.alcanzado()) + " floor cells can't be walked to from the door, such as the one at " + texto(a.primera())));
            }
            if (!a.afuera()) {
                out.add(new Problema("desde la entrada no se sale a ningún lado", "the entrance leads nowhere"));
            }
        }
        return new Revision(List.copyOf(out), n, bloques, ancho, fondo, alto, a.suelo(), a.alcanzado(), a.pisos());
    }

    /** One door: its halves matching, on firm floor; the entrance right in front of it; both sides of it kept clear. */
    private static void puerta(Map<BlockPos, Plano.Pieza> c, BlockPos entrada, List<Problema> out) {
        BlockPos baja = null;
        int puertas = 0;
        int arribas = 0;
        for (Plano.Pieza p : c.values()) {
            if (p.rol() == Plano.Rol.PUERTA) {
                puertas++;
                baja = p.rel();
            } else if (p.rol() == Plano.Rol.PUERTA_ARRIBA) {
                arribas++;
            }
        }
        if (puertas != 1 || arribas != 1 || baja == null) {
            out.add(new Problema("tiene " + puertas + " puertas: necesita una", "it has " + puertas + " doors: it needs one"));
            return;
        }
        BlockState abajo = c.get(baja).state();
        Plano.Pieza alta = c.get(baja.up());
        if (alta == null || alta.rol() != Plano.Rol.PUERTA_ARRIBA) {
            out.add(new Problema("la mitad de arriba de la puerta no está sobre la de abajo", "the upper half of the door is not on the lower one"));
            return;
        }
        if (!abajo.contains(DoorBlock.FACING) || !igual(abajo, alta.state(), DoorBlock.FACING) || !igual(abajo, alta.state(), DoorBlock.HINGE)
                || abajo.get(DoorBlock.HALF) != DoubleBlockHalf.LOWER || alta.state().get(DoorBlock.HALF) != DoubleBlockHalf.UPPER) {
            out.add(new Problema("las dos mitades de la puerta no calzan", "the two halves of the door don't match"));
            return;
        }
        if (!entero(c.get(baja.down()))) {
            out.add(new Problema("la puerta no tiene piso firme debajo", "the door has no firm floor under it"));
        }
        Direction adentro = abajo.get(DoorBlock.FACING);
        BlockPos afuera = baja.offset(adentro.getOpposite());
        if (!afuera.equals(entrada)) {
            out.add(new Problema("la entrada (" + texto(entrada) + ") no está frente a la puerta", "the entrance (" + texto(entrada) + ") is not in front of the door"));
        }
        BlockPos dentro = baja.offset(adentro);
        for (BlockPos p : new BlockPos[]{afuera, afuera.up(), dentro, dentro.up()}) {
            Plano.Pieza q = c.get(p);
            if (q == null || q.rol() != Plano.Rol.AIRE) {
                out.add(new Problema("a los dos lados de la puerta hay que dejar libre; " + texto(p) + " no lo está",
                        "both sides of the door must be kept clear; " + texto(p) + " isn't"));
                return;
            }
        }
    }

    private static <T extends Comparable<T>> boolean igual(BlockState a, BlockState b, Property<T> p) {
        return a.contains(p) && b.contains(p) && a.get(p).equals(b.get(p));
    }

    /** The block a torch hangs on: the wall behind a wall torch, the floor under a standing one. */
    private static BlockPos apoyo(Plano.Pieza p) {
        return p.state().contains(Properties.HORIZONTAL_FACING) ? p.rel().offset(p.state().get(Properties.HORIZONTAL_FACING).getOpposite()) : p.rel().down();
    }

    /** A whole block with whole faces: what a door stands on and a torch hangs from. */
    private static boolean entero(@Nullable Plano.Pieza q) {
        return q != null && (q.rol() == Plano.Rol.BASE || q.rol() == Plano.Rol.TRONCO || q.rol() == Plano.Rol.TABLA || q.rol() == Plano.Rol.MESA);
    }

    /** A block a builder can set another against (its eye on one of its faces). */
    private static boolean cara(@Nullable Plano.Pieza q) {
        return entero(q) || q != null && q.rol() == Plano.Rol.ESCALON;
    }

    /** The ground round and under the house: whatever is at or below the footing's level and not in the plan. */
    private static boolean tierra(Map<BlockPos, Plano.Pieza> c, BlockPos p) {
        return p.getY() < 0 || p.getY() == 0 && !c.containsKey(p);
    }

    /**
     * The first block a builder could never set, or null when there is an order for all of them: layer by layer (a
     * frame block no more than one layer above the lowest one still missing, as the builder works), each against one
     * set before or the ground; a door on whole floor, a torch on its wall.
     */
    @Nullable
    private static BlockPos sinApoyo(Map<BlockPos, Plano.Pieza> c) {
        List<Plano.Pieza> pend = new ArrayList<>();
        for (Plano.Pieza p : c.values()) {
            if (p.rol().bloque()) {
                pend.add(p);
            }
        }
        pend.sort(Comparator.comparingInt((Plano.Pieza p) -> p.rel().getY()).thenComparingInt(p -> p.rel().getZ()).thenComparingInt(p -> p.rel().getX()));
        Set<BlockPos> puestas = new HashSet<>();
        boolean avance = true;
        while (!pend.isEmpty() && avance) {
            avance = false;
            int techo = Integer.MAX_VALUE;
            for (Plano.Pieza p : pend) {
                if (p.rol().estructura) {
                    techo = Math.min(techo, p.rel().getY() + 1);
                }
            }
            for (Iterator<Plano.Pieza> it = pend.iterator(); it.hasNext(); ) {
                Plano.Pieza p = it.next();
                if (p.rol().estructura && p.rel().getY() > techo) {
                    continue;
                }
                if (apoyada(c, puestas, p)) {
                    puestas.add(p.rel());
                    if (p.rol() == Plano.Rol.PUERTA) {
                        puestas.add(p.rel().up());
                    }
                    it.remove();
                    avance = true;
                }
            }
        }
        return pend.isEmpty() ? null : pend.get(0).rel();
    }

    private static boolean apoyada(Map<BlockPos, Plano.Pieza> c, Set<BlockPos> puestas, Plano.Pieza p) {
        BlockPos at = p.rel();
        if (p.rol() == Plano.Rol.PUERTA) {
            return puestas.contains(at.down()) && entero(c.get(at.down()));
        }
        if (p.rol() == Plano.Rol.ANTORCHA) {
            BlockPos w = apoyo(p);
            return puestas.contains(w) && entero(c.get(w));
        }
        for (Direction d : Direction.values()) {
            BlockPos n = at.offset(d);
            if (tierra(c, n) || puestas.contains(n) && cara(c.get(n))) {
                return true;
            }
        }
        return false;
    }

    private record Acceso(boolean desde, int suelo, int alcanzado, int pisos, boolean afuera, @Nullable BlockPos primera) {
    }

    /** A body fits in this cell: empty, a door, a torch, or open air off the plan above the ground. */
    private static boolean pasa(Map<BlockPos, Plano.Pieza> c, BlockPos p) {
        Plano.Pieza q = c.get(p);
        if (q == null) {
            return !tierra(c, p);
        }
        return q.rol() == Plano.Rol.AIRE || q.rol() == Plano.Rol.PUERTA || q.rol() == Plano.Rol.PUERTA_ARRIBA || q.rol() == Plano.Rol.ANTORCHA;
    }

    /** Something to stand on: a block of the plan (a stair, a table) or the ground. */
    private static boolean pisa(Map<BlockPos, Plano.Pieza> c, BlockPos p) {
        Plano.Pieza q = c.get(p);
        if (q == null) {
            return tierra(c, p);
        }
        return cara(q) || q.rol() == Plano.Rol.COFRE;
    }

    private static boolean parado(Map<BlockPos, Plano.Pieza> c, BlockPos f) {
        return pasa(c, f) && pasa(c, f.up()) && pisa(c, f.down());
    }

    /** Floor: plank, log or stone, the kind a room is laid on. */
    private static boolean piso(@Nullable Plano.Pieza q) {
        return q != null && (q.rol() == Plano.Rol.BASE || q.rol() == Plano.Rol.TRONCO || q.rol() == Plano.Rol.TABLA);
    }

    /**
     * Walking from the entrance: level, up a stair from its low side, or down one; never onto a block that would need
     * a jump. How many of the floor cells (empty, over plank, log or stone, with room for a head) it reaches, on how
     * many levels, and whether it gets off the plan's ground.
     */
    private static Acceso acceso(Map<BlockPos, Plano.Pieza> c, BlockPos entrada, int x0, int x1, int z0, int z1, int y1) {
        if (!parado(c, entrada)) {
            return new Acceso(false, 0, 0, 0, false, null);
        }
        Set<BlockPos> vistos = new HashSet<>();
        ArrayDeque<BlockPos> cola = new ArrayDeque<>();
        vistos.add(entrada);
        cola.add(entrada);
        boolean afuera = false;
        while (!cola.isEmpty()) {
            BlockPos f = cola.poll();
            if (f.getX() < x0 || f.getX() > x1 || f.getZ() < z0 || f.getZ() > z1) {
                afuera = true;
            }
            for (Direction d : Direction.Type.HORIZONTAL) {
                BlockPos n = f.offset(d);
                if (n.getX() < x0 - 2 || n.getX() > x1 + 2 || n.getZ() < z0 - 2 || n.getZ() > z1 + 2 || n.getY() > y1 + 2) {
                    continue;
                }
                BlockPos to = null;
                Plano.Pieza s = c.get(n);
                if (parado(c, n)) {
                    to = n;
                } else if (s != null && s.rol() == Plano.Rol.ESCALON && s.state().contains(Properties.HORIZONTAL_FACING)
                        && s.state().get(Properties.HORIZONTAL_FACING) == d
                        && (!s.state().contains(Properties.BLOCK_HALF) || s.state().get(Properties.BLOCK_HALF) == BlockHalf.BOTTOM) && parado(c, n.up())) {
                    to = n.up();
                } else if (pasa(c, n) && pasa(c, n.up()) && parado(c, n.down())) {
                    to = n.down();
                }
                if (to != null && vistos.add(to)) {
                    cola.add(to);
                }
            }
        }
        int suelo = 0;
        int alcanzado = 0;
        Set<Integer> niveles = new HashSet<>();
        BlockPos primera = null;
        for (Plano.Pieza p : c.values()) {
            if (p.rol() != Plano.Rol.AIRE || !piso(c.get(p.rel().down())) || !pasa(c, p.rel().up())) {
                continue;
            }
            suelo++;
            if (vistos.contains(p.rel())) {
                alcanzado++;
                niveles.add(p.rel().getY());
            } else if (primera == null || antes(p.rel(), primera)) {
                primera = p.rel();
            }
        }
        return new Acceso(true, suelo, alcanzado, niveles.size(), afuera, primera);
    }

    private static boolean antes(BlockPos a, BlockPos b) {
        if (a.getY() != b.getY()) {
            return a.getY() < b.getY();
        }
        if (a.getZ() != b.getZ()) {
            return a.getZ() < b.getZ();
        }
        return a.getX() < b.getX();
    }

    public static String texto(@Nullable BlockPos p) {
        return p == null ? "?" : p.getX() + " " + p.getY() + " " + p.getZ();
    }
}
