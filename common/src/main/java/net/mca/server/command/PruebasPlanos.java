package net.mca.server.command;

import net.mca.entity.ai.obra.Obra;
import net.mca.entity.ai.obra.ObrasMundo;
import net.mca.entity.ai.obra.Plano;
import net.mca.entity.ai.obra.PlanoCodec;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Data and real world-save checks, not a claim that a villager built the fixture. */
final class PruebasPlanos {
    private static final String PROJECT = "sol-plano-reinicio";
    private static final UUID BUILDER = UUID.fromString("b5d70ce7-1b32-4279-9299-b84de9c0a649");
    private static final BlockPos ORIGIN = new BlockPos(31000, 100, 31000);

    static void run(ServerWorld world, String which) {
        if ("planos-reinicio".equals(which)) {
            caso(which, () -> reinicio(world));
            return;
        }
        caso("planos-legacy", PruebasPlanos::legacy);
        caso("planos-snapshot", PruebasPlanos::snapshot);
        caso("planos-inmutables", PruebasPlanos::inmutables);
        caso("planos-validacion", PruebasPlanos::validacion);
        caso("planos-codec", PruebasPlanos::codec);
        caso("planos-anclas", PruebasPlanos::anclas);
        caso("planos-cuarentena", PruebasPlanos::cuarentena);
        caso("planos-preparar", () -> preparar(world));
    }

    private static void caso(String name, Runnable check) {
        try {
            check.run();
            Pruebas.check(name, true, "invariantes verificadas");
        } catch (RuntimeException e) {
            Pruebas.check(name, false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void exige(boolean condition, String why) {
        if (!condition) {
            throw new IllegalStateException(why);
        }
    }

    private static void rechaza(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException e) {
            return;
        }
        throw new IllegalStateException("Se aceptaron datos inválidos");
    }

    private static List<Plano.Pieza> piezas() {
        List<Plano.Pieza> p = new ArrayList<>();
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 6; z++) {
                p.add(new Plano.Pieza(new BlockPos(x, 0, z), Blocks.COBBLESTONE.getDefaultState(), Plano.Rol.BASE));
            }
        }
        p.add(new Plano.Pieza(new BlockPos(2, 1, 0), Blocks.OAK_DOOR.getDefaultState(), Plano.Rol.PUERTA));
        p.add(new Plano.Pieza(new BlockPos(2, 2, 0), Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.HALF, DoubleBlockHalf.UPPER), Plano.Rol.PUERTA_ARRIBA));
        p.add(new Plano.Pieza(new BlockPos(0, 2, 2), Blocks.GLASS_PANE.getDefaultState().with(Properties.NORTH, true), Plano.Rol.VIDRIO));
        p.add(new Plano.Pieza(new BlockPos(4, 1, 6), Blocks.OAK_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.X), Plano.Rol.TRONCO));
        p.add(new Plano.Pieza(new BlockPos(3, 1, 3), Blocks.OAK_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST), Plano.Rol.ESCALON));
        p.add(new Plano.Pieza(new BlockPos(2, 1, -1), Blocks.AIR.getDefaultState(), Plano.Rol.AIRE));
        p.add(new Plano.Pieza(new BlockPos(2, 2, -1), Blocks.AIR.getDefaultState(), Plano.Rol.AIRE));
        return p;
    }

    private static Plano crear(List<Plano.Pieza> piezas) {
        return Plano.crear("tropeles:prueba/asimetrico", 3, "plano de prueba", "test blueprint", new BlockPos(2, 0, 4), new BlockPos(2, 1, -1), piezas);
    }

    private static Obra obra(Plano p, Direction frente) {
        return new Obra(PROJECT, BUILDER, "Prueba plano", ORIGIN, frente, Plano.Madera.ROBLE, p);
    }

    private static void legacy() {
        for (Plano.Madera wood : Plano.Madera.values()) {
            Plano p = Plano.casa(wood);
            exige(p.materiales.get(Plano.Rol.VIDRIO) == 15 && p.materiales.get(Plano.Rol.ANTORCHA) == 4, "Cambió el modelo legacy");
            for (Direction dir : Direction.Type.HORIZONTAL) {
                Obra original = new Obra(PROJECT, BUILDER, "Legacy", ORIGIN, dir, wood);
                NbtCompound old = original.guardar();
                old.remove("plano");
                old.putBoolean("pausaJugador", true);
                old.putInt("version", 12);
                old.putLongArray("andamios", new long[]{ORIGIN.up().asLong()});
                Obra read = Obra.leer(old);
                exige(read != null && read.plano().piezas.equals(p.piezas) && original.celdas().equals(read.celdas()), "La migración cambió celdas/estados");
                exige(read.pausaJugador && read.version == 12 && read.andamios.contains(ORIGIN.up()), "La migración perdió progreso auxiliar");
                exige(read.guardar().contains("plano", NbtElement.COMPOUND_TYPE), "No se guarda el snapshot migrado");
            }
        }
    }

    private static void snapshot() {
        Plano custom = crear(piezas());
        Obra original = obra(custom, Direction.EAST);
        // Changing the old supply hint must not select another geometry.
        original.madera = Plano.Madera.CEREZO;
        Obra read = Obra.leer(original.guardar());
        exige(read != null && read.plano().id.equals(custom.id) && read.plano().hash().equals(custom.hash()), "Se resolvió otro diseño");
        exige(read.celdas().equals(original.celdas()) && read.entrada().equals(original.entrada()), "El snapshot perdió geometría");
        exige(!read.plano().hash().equals(Plano.casa(Plano.Madera.CEREZO).hash()), "Se volvió al catálogo por madera");
    }

    private static void inmutables() {
        List<Plano.Pieza> cells = piezas();
        BlockPos.Mutable mutable = new BlockPos.Mutable(1, 2, 4);
        cells.add(new Plano.Pieza(mutable, Blocks.AIR.getDefaultState(), Plano.Rol.AIRE));
        Plano p = crear(cells);
        String before = p.hash();
        mutable.set(63, 63, 63);
        cells.clear();
        exige(p.piezas.stream().anyMatch(c -> c.rel().equals(new BlockPos(1, 2, 4))) && p.hash().equals(before), "El llamador mutó el plano");
        try {
            p.piezas.clear();
            throw new IllegalStateException("Lista mutable");
        } catch (UnsupportedOperationException expected) {
            // Required: the public list cannot mutate the frozen geometry.
        }
        NbtCompound exported = PlanoCodec.guardar(p);
        exported.getList("piezas", NbtElement.COMPOUND_TYPE).clear();
        exige(PlanoCodec.leer(PlanoCodec.guardar(p)).hash().equals(before), "Mutar el NBT exportado modificó el plano");
        List<Plano.Pieza> shuffled = new ArrayList<>(p.piezas);
        Collections.reverse(shuffled);
        exige(crear(shuffled).hash().equals(before), "El orden de entrada cambió la identidad geométrica");
    }

    private static void validacion() {
        rechaza(() -> crear(List.of()));
        rechaza(() -> Plano.crear("sin namespace", 1, "x", "x", BlockPos.ORIGIN, new BlockPos(0, 1, -1), piezas()));
        rechaza(() -> Plano.crear("tropeles:test", 0, "x", "x", BlockPos.ORIGIN, new BlockPos(0, 1, -1), piezas()));
        List<Plano.Pieza> duplicate = piezas();
        duplicate.add(duplicate.get(0));
        rechaza(() -> crear(duplicate));
        rechaza(() -> crear(Collections.nCopies(Plano.MAX_PIEZAS + 1, piezas().get(0))));
        List<Plano.Pieza> out = piezas();
        out.add(new Plano.Pieza(new BlockPos(65, 0, 0), Blocks.COBBLESTONE.getDefaultState(), Plano.Rol.BASE));
        rechaza(() -> crear(out));
        List<Plano.Pieza> wrong = piezas();
        wrong.set(0, new Plano.Pieza(BlockPos.ORIGIN, Blocks.AIR.getDefaultState(), Plano.Rol.BASE));
        rechaza(() -> crear(wrong));
        List<Plano.Pieza> door = piezas();
        door.removeIf(p -> p.rol() == Plano.Rol.PUERTA_ARRIBA);
        rechaza(() -> crear(door));
        List<Plano.Pieza> entry = piezas();
        entry.removeIf(p -> p.rel().equals(new BlockPos(2, 2, -1)));
        rechaza(() -> crear(entry));
    }

    private static void codec() {
        Plano p = crear(piezas());
        NbtCompound saved = PlanoCodec.guardar(p);
        Plano read = PlanoCodec.leer(saved);
        exige(read.piezas.equals(p.piezas), "No conservó estados/propiedades/roles");
        NbtCompound future = saved.copy();
        future.putInt("formato", 999);
        rechaza(() -> PlanoCodec.leer(future));
        NbtCompound changed = saved.copy();
        changed.putString("hash", "0".repeat(64));
        rechaza(() -> PlanoCodec.leer(changed));
        NbtCompound block = saved.copy();
        block.getList("piezas", NbtElement.COMPOUND_TYPE).getCompound(0).putString("bloque", "tropeles:bloque_ausente");
        rechaza(() -> PlanoCodec.leer(block));
        NbtCompound prop = saved.copy();
        prop.getList("piezas", NbtElement.COMPOUND_TYPE).getCompound(0).getCompound("propiedades").putString("facing", "north");
        rechaza(() -> PlanoCodec.leer(prop));
        NbtCompound pos = saved.copy();
        pos.putIntArray("entrada", new int[]{1, 2});
        rechaza(() -> PlanoCodec.leer(pos));
        Obra o = obra(p, Direction.NORTH);
        NbtCompound badProject = o.guardar();
        badProject.put("plano", future);
        exige(Obra.leer(badProject) == null, "Snapshot futuro reemplazado por casa legacy");
        badProject.putString("plano", "no es un compuesto");
        exige(Obra.leer(badProject) == null, "Snapshot corrupto reemplazado por casa legacy");
    }

    private static BlockPos rotar(BlockPos p, Direction dir) {
        return switch (dir) {
            case EAST -> new BlockPos(-p.getZ(), p.getY(), p.getX());
            case SOUTH -> new BlockPos(-p.getX(), p.getY(), -p.getZ());
            case WEST -> new BlockPos(p.getZ(), p.getY(), -p.getX());
            default -> p;
        };
    }

    private static void anclas() {
        Plano p = crear(piezas());
        for (Direction d : Direction.Type.HORIZONTAL) {
            Obra o = obra(p, d);
            exige(o.centro().equals(ORIGIN.add(rotar(new BlockPos(2, 1, 4), d))), "Centro fijo en 3,3");
            exige(o.entrada().equals(ORIGIN.add(rotar(new BlockPos(2, 1, -1), d))), "Entrada legacy en plano nuevo");
            BlockPos oldEntry = o.entrada();
            o.celdas();
            o.mover(ORIGIN.east(20), d);
            exige(o.entrada().equals(oldEntry.east(20)) && o.en(o.entrada()) != null && o.en(oldEntry) == null, "Caché de colocación obsoleta");
            exige(o.plano().hash().equals(p.hash()), "Mover la obra cambió el diseño");
        }
    }

    private static void cuarentena() {
        Obra valid = obra(crear(piezas()), Direction.NORTH);
        NbtCompound bad = valid.guardar();
        bad.putString("id", "incompatible");
        bad.getCompound("plano").putInt("formato", 999);
        NbtList records = new NbtList();
        records.add(valid.guardar());
        records.add(bad.copy());
        NbtCompound root = new NbtCompound();
        root.put("obras", records);
        ObrasMundo loaded = ObrasMundo.leer(root);
        exige(loaded.porId(PROJECT) != null && loaded.porId("incompatible") == null && loaded.tienePlanosIncompatibles(), "No aisló la obra incompatible");
        NbtCompound again = loaded.writeNbt(new NbtCompound());
        NbtList retained = again.getList("obras", NbtElement.COMPOUND_TYPE);
        exige(retained.size() == 2 && retained.getCompound(1).equals(bad), "Se perdió o alteró el registro incompatible");
        exige(ObrasMundo.leer(again).tienePlanosIncompatibles(), "Otro reinicio habilitó otra casa");
        NbtCompound corrupt = new NbtCompound();
        corrupt.putString("obras", "lista futura");
        ObrasMundo unavailable = ObrasMundo.leer(corrupt);
        exige(unavailable.tienePlanosIncompatibles() && unavailable.writeNbt(new NbtCompound()).equals(corrupt), "Lista desconocida borrada");
    }

    private static void preparar(ServerWorld world) {
        ObrasMundo store = ObrasMundo.get(world);
        exige(store.porId(PROJECT) == null, "La prueba requiere primer proceso sin fixture anterior");
        store.poner(obra(crear(piezas()), Direction.WEST));
        exige(store.porId(PROJECT) != null, "No registró la obra para reiniciar");
    }

    private static void reinicio(ServerWorld world) {
        ObrasMundo store = ObrasMundo.get(world);
        Obra o = store.porId(PROJECT);
        exige(o != null, "No leyó la obra del primer proceso (no se recrea)");
        Plano expected = crear(piezas());
        exige(o.constructor.equals(BUILDER) && o.origen.equals(ORIGIN) && o.frente == Direction.WEST, "Perdió identidad/colocación");
        exige(o.plano().hash().equals(expected.hash()) && o.plano().piezas.equals(expected.piezas), "Perdió snapshot personalizado al reiniciar");
        exige(!o.plano().id.equals(Plano.casa(o.madera).id), "Sustituyó por casa del catálogo");
        store.quitar(o);
    }
}
