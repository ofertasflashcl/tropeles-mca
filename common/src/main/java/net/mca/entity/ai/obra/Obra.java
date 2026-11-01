package net.mca.entity.ai.obra;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A building somebody is putting up: which plan, where and which way round, who builds it and for whom, and what
 * the builder set down around it (scaffolds, a table, a furnace, a quarry). How far along it is is never stored:
 * it is read from the world, cell by cell ({@link Plano#hecho}), so whatever a player adds to it counts as well.
 */
public final class Obra {
    /** One cell of the building in the world: where, the exact state, and its kind. */
    public record Celda(BlockPos pos, BlockState state, Plano.Rol rol) {
    }

    public final String id;
    public final UUID constructor;
    public String constructorNombre;
    @Nullable
    public UUID para;
    public String paraNombre;
    public BlockPos origen;
    public Direction frente;
    public Plano.Madera madera;
    /** A player told the builder to do something else: it does not go back to it by itself. */
    public boolean pausaJugador;
    /** There is no sand anywhere it could look: the windows stay open. */
    public boolean sinVidrio;
    public long creada;
    /** Bumped every time the plan moves (the preview is sent again). */
    public int version;
    /** The blocks it climbed on, to take back down. */
    public final Set<BlockPos> andamios = new LinkedHashSet<>();
    /** The crafting table and furnace it set down to work, to take back at the end. */
    @Nullable
    public BlockPos mesa;
    @Nullable
    public BlockPos horno;
    /** Its quarry: where the staircase starts, which way it goes down, and how many steps are cut. */
    @Nullable
    public BlockPos cantera;
    public Direction canteraDir = Direction.NORTH;
    public int canteraPasos;

    private transient List<Celda> celdas;
    private transient Map<Long, Celda> porPos;
    private transient int minX;
    private transient int minY;
    private transient int minZ;
    private transient int maxX;
    private transient int maxY;
    private transient int maxZ;

    public Obra(String id, UUID constructor, String constructorNombre, BlockPos origen, Direction frente, Plano.Madera madera) {
        this.id = id;
        this.constructor = constructor;
        this.constructorNombre = constructorNombre;
        this.origen = origen.toImmutable();
        this.frente = frente;
        this.madera = madera;
        this.paraNombre = "";
    }

    public Plano plano() {
        return Plano.casa(madera);
    }

    public BlockRotation giro() {
        return Plano.giro(frente);
    }

    /** Moves the plan (nothing of it is built yet): the cells are worked out again. */
    public void mover(BlockPos nuevo, Direction nuevoFrente) {
        origen = nuevo.toImmutable();
        frente = nuevoFrente;
        celdas = null;
        version++;
    }

    /** Every cell of the building in the world, blocks bottom up, then the cells that must stay empty. */
    public List<Celda> celdas() {
        if (celdas == null) {
            BlockRotation r = giro();
            List<Celda> list = new ArrayList<>();
            Map<Long, Celda> map = new HashMap<>();
            int x0 = Integer.MAX_VALUE;
            int y0 = Integer.MAX_VALUE;
            int z0 = Integer.MAX_VALUE;
            int x1 = Integer.MIN_VALUE;
            int y1 = Integer.MIN_VALUE;
            int z1 = Integer.MIN_VALUE;
            for (Plano.Pieza p : plano().piezas) {
                BlockPos at = Plano.enMundo(origen, r, p.rel()).toImmutable();
                Celda c = new Celda(at, p.state().rotate(r), p.rol());
                list.add(c);
                map.put(at.asLong(), c);
                x0 = Math.min(x0, at.getX());
                y0 = Math.min(y0, at.getY());
                z0 = Math.min(z0, at.getZ());
                x1 = Math.max(x1, at.getX());
                y1 = Math.max(y1, at.getY());
                z1 = Math.max(z1, at.getZ());
            }
            minX = x0;
            minY = y0;
            minZ = z0;
            maxX = x1;
            maxY = y1;
            maxZ = z1;
            porPos = map;
            celdas = Collections.unmodifiableList(list);
        }
        return celdas;
    }

    /** The cell of the building at {@code pos}, or null when it is not part of it. */
    @Nullable
    public Celda en(BlockPos pos) {
        celdas();
        return porPos.get(pos.asLong());
    }

    /** A block or empty cell of the building. */
    public boolean ocupa(BlockPos pos) {
        return en(pos) != null;
    }

    /** Within the building's box, or {@code margen} blocks round it. */
    public boolean cerca(BlockPos pos, int margen) {
        celdas();
        return pos.getX() >= minX - margen && pos.getX() <= maxX + margen && pos.getZ() >= minZ - margen && pos.getZ() <= maxZ + margen
                && pos.getY() >= minY - margen && pos.getY() <= maxY + margen;
    }

    public BlockPos min() {
        celdas();
        return new BlockPos(minX, minY, minZ);
    }

    public BlockPos max() {
        celdas();
        return new BlockPos(maxX, maxY, maxZ);
    }

    /** The middle of the ground floor. */
    public BlockPos centro() {
        return Plano.enMundo(origen, giro(), Plano.CENTRO_CASA.up());
    }

    /** The cell in front of the door, outside. */
    public BlockPos entrada() {
        return Plano.enMundo(origen, giro(), new BlockPos(3, 1, -1));
    }

    /** How many blocks it has in all (the door once). */
    public int total() {
        int n = 0;
        for (Celda c : celdas()) {
            if (c.rol().bloque()) {
                n++;
            }
        }
        return n;
    }

    /** Whether this cell is done in the world as it is now. */
    public static boolean hecha(World world, Celda c) {
        return Plano.hecho(c.rol(), c.state(), world.getBlockState(c.pos()));
    }

    /** How many blocks are done (the door once). */
    public int hechas(World world) {
        int n = 0;
        for (Celda c : celdas()) {
            if (c.rol().bloque() && hecha(world, c)) {
                n++;
            }
        }
        return n;
    }

    /** What is still to be set, by kind (the windows are left out when there is no sand to be had). */
    public Map<Plano.Rol, Integer> faltan(World world) {
        Map<Plano.Rol, Integer> m = new EnumMap<>(Plano.Rol.class);
        for (Celda c : celdas()) {
            if (c.rol().bloque() && !(sinVidrio && c.rol() == Plano.Rol.VIDRIO) && !hecha(world, c)) {
                m.merge(c.rol(), 1, Integer::sum);
            }
        }
        return m;
    }

    public String donde() {
        BlockPos c = centro();
        return c.getX() + " " + c.getY() + " " + c.getZ();
    }

    // ------------------------------------------------------------------ saving

    public NbtCompound guardar() {
        NbtCompound n = new NbtCompound();
        n.putString("id", id);
        n.putUuid("constructor", constructor);
        n.putString("constructorNombre", constructorNombre == null ? "" : constructorNombre);
        if (para != null) {
            n.putUuid("para", para);
        }
        n.putString("paraNombre", paraNombre == null ? "" : paraNombre);
        n.putLong("origen", origen.asLong());
        n.putString("frente", frente.asString());
        n.putString("madera", madera.name());
        n.putBoolean("pausaJugador", pausaJugador);
        n.putBoolean("sinVidrio", sinVidrio);
        n.putLong("creada", creada);
        n.putInt("version", version);
        n.putLongArray("andamios", andamios.stream().mapToLong(BlockPos::asLong).toArray());
        if (mesa != null) {
            n.putLong("mesa", mesa.asLong());
        }
        if (horno != null) {
            n.putLong("horno", horno.asLong());
        }
        if (cantera != null) {
            n.putLong("cantera", cantera.asLong());
            n.putString("canteraDir", canteraDir.asString());
            n.putInt("canteraPasos", canteraPasos);
        }
        return n;
    }

    @Nullable
    public static Obra leer(NbtCompound n) {
        try {
            Direction frente = Direction.byName(n.getString("frente"));
            Plano.Madera madera = Plano.Madera.valueOf(n.getString("madera"));
            Obra o = new Obra(n.getString("id"), n.getUuid("constructor"), n.getString("constructorNombre"), BlockPos.fromLong(n.getLong("origen")),
                    frente == null || frente.getAxis().isVertical() ? Direction.NORTH : frente, madera);
            if (n.containsUuid("para")) {
                o.para = n.getUuid("para");
            }
            o.paraNombre = n.getString("paraNombre");
            o.pausaJugador = n.getBoolean("pausaJugador");
            o.sinVidrio = n.getBoolean("sinVidrio");
            o.creada = n.getLong("creada");
            o.version = n.getInt("version");
            for (long l : n.getLongArray("andamios")) {
                o.andamios.add(BlockPos.fromLong(l));
            }
            if (n.contains("mesa", NbtElement.LONG_TYPE)) {
                o.mesa = BlockPos.fromLong(n.getLong("mesa"));
            }
            if (n.contains("horno", NbtElement.LONG_TYPE)) {
                o.horno = BlockPos.fromLong(n.getLong("horno"));
            }
            if (n.contains("cantera", NbtElement.LONG_TYPE)) {
                o.cantera = BlockPos.fromLong(n.getLong("cantera"));
                Direction d = Direction.byName(n.getString("canteraDir"));
                o.canteraDir = d == null || d.getAxis().isVertical() ? Direction.NORTH : d;
                o.canteraPasos = n.getInt("canteraPasos");
            }
            return o;
        } catch (RuntimeException e) {
            return null;
        }
    }

    static NbtList lista(Iterable<Obra> obras) {
        NbtList list = new NbtList();
        for (Obra o : obras) {
            list.add(o.guardar());
        }
        return list;
    }
}
