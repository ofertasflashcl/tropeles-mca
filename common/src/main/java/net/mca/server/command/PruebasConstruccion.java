package net.mca.server.command;

import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.body.Progreso;
import net.mca.entity.ai.chatAI.TriggerCommandInfos;
import net.mca.entity.ai.obra.CatalogoPlanos;
import net.mca.entity.ai.obra.Constructor;
import net.mca.entity.ai.obra.Obra;
import net.mca.entity.ai.obra.PedidoCasa;
import net.mca.entity.ai.obra.Plano;
import net.mca.entity.ai.obra.Taller;
import net.mca.entity.ai.sense.Retina;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** Uses the production placer/crafter. Positioning the actor in the placement fixture is not autonomous navigation. */
final class PruebasConstruccion {
    private static final int CLEAN = Block.FORCE_STATE | Block.NOTIFY_LISTENERS;

    static void run(ServerWorld world) {
        seguro("obra-paleta", PruebasConstruccion::paleta);
        seguro("obra-seleccion", PruebasConstruccion::seleccion);
        seguro("obra-puerta", () -> puertas(world));
        seguro("obra-fabricacion", () -> fabricacion(world));
        Pruebas.start(new Modelos(world));
        Pruebas.start(new Madera(world));
        Pruebas.start(new Escolta(world));
    }

    private static void seguro(String name, Runnable test) {
        try {
            test.run();
            Pruebas.check(name, true, "comprobado con el contrato/colocador real");
        } catch (RuntimeException e) {
            Pruebas.check(name, false, e.toString());
        }
    }

    private static void exige(boolean condition, String detail) {
        if (!condition) throw new IllegalStateException(detail);
    }

    private static int contar(SimpleInventory inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.size(); i++) if (inv.getStack(i).isOf(item)) n += inv.getStack(i).getCount();
        return n;
    }

    private static void paleta() {
        for (Plano.Madera m : Plano.Madera.values()) {
            for (Plano.Pieza p : Plano.casa(m).piezas) {
                if (!p.rol().bloque()) continue;
                exige(Plano.hecho(p.rol(), p.state(), p.state()), "El estado exacto no cuenta");
                exige(Plano.acepta(p.rol(), p.state(), new ItemStack(Plano.item(p.rol(), p.state()))), "No admite su objeto exacto");
                if (p.rol() == Plano.Rol.TRONCO) {
                    for (Plano.Madera other : Plano.Madera.values()) {
                        if (other == m) continue;
                        exige(!Plano.acepta(p.rol(), p.state(), new ItemStack(other.log.asItem())), "Acepta otra especie");
                        exige(!Plano.hecho(p.rol(), p.state(), other.log.getDefaultState()), "Cuenta otro tronco como terminado");
                    }
                    exige(!Plano.hecho(p.rol(), p.state(), p.state().with(Properties.AXIS, Direction.Axis.X)), "Ignora el eje");
                }
            }
        }
        exige(!Plano.hecho(Plano.Rol.BASE, Blocks.COBBLESTONE.getDefaultState(), Blocks.GRANITE.getDefaultState()), "Base de otro bloque");
        exige(!Plano.hecho(Plano.Rol.COFRE, Blocks.CHEST.getDefaultState(), Blocks.BARREL.getDefaultState()), "Barril en vez de cofre");
        exige(!Plano.hecho(Plano.Rol.VIDRIO, Blocks.GLASS_PANE.getDefaultState(), Blocks.GLASS.getDefaultState()), "Bloque en vez de panel");
    }

    private static void seleccion() {
        exige(PedidoCasa.elegir("Hazme una cabaña de abeto", Plano.Madera.ABETO).id.equals("tropeles:casa/cabana"), "Cabaña perdida");
        exige(PedidoCasa.elegir("build a long house", Plano.Madera.CEREZO).id.equals("tropeles:casa/larga"), "Casa larga perdida");
        exige(PedidoCasa.elegir("casa alargada", Plano.Madera.ROBLE).id.equals("tropeles:casa/larga"), "Casa alargada perdida");
        exige(PedidoCasa.elegir("haz una casa", Plano.Madera.ROBLE).id.equals(Plano.casa(Plano.Madera.ROBLE).id), "Cambió la opción por defecto");
    }

    private static void puertas(ServerWorld world) {
        BlockPos origin = new BlockPos(21000, 100, 100);
        Pruebas.load(world, origin, 80);
        Pruebas.fill(world, origin.add(-8, 0, -8), origin.add(72, 0, 40), Blocks.STONE.getDefaultState());
        Pruebas.fill(world, origin.add(-8, 1, -8), origin.add(72, 5, 40), Blocks.AIR.getDefaultState());
        VillagerEntityMCA v = Pruebas.villager(world, origin.up(), Direction.NORTH, "Puerta fiel");
        try {
            int i = 0;
            for (Plano.Madera m : Plano.Madera.values()) {
                Plano.Pieza door = Plano.casa(m).piezas.stream().filter(p -> p.rol() == Plano.Rol.PUERTA).findFirst().orElseThrow();
                for (Direction dir : Direction.Type.HORIZONTAL) {
                    BlockPos at = origin.add((i % 8) * 8, 1, (i / 8) * 8);
                    i++;
                    BlockState state = door.state().rotate(Plano.giro(dir));
                    Obra.Celda cell = new Obra.Celda(at, state, Plano.Rol.PUERTA);
                    v.refreshPositionAndAngles(at.offset(dir, 2), dir.getOpposite().asRotation(), 0);
                    v.getInventory().clear();
                    Plano.Madera other = m == Plano.Madera.ROBLE ? Plano.Madera.ABETO : Plano.Madera.ROBLE;
                    v.getInventory().setStack(0, new ItemStack(other.door.asItem(), 3));
                    exige(!Constructor.colocar(world, v, cell) && world.isAir(at) && contar(v.getInventory(), other.door.asItem()) == 3, "Usó una puerta equivocada");
                    v.getInventory().setStack(1, new ItemStack(m.door.asItem(), 3));
                    exige(Constructor.colocar(world, v, cell) && Obra.hecha(world, cell), "No puso ambas mitades: " + m + " " + dir);
                    exige(contar(v.getInventory(), m.door.asItem()) == 2 && contar(v.getInventory(), other.door.asItem()) == 3, "Consumo de puerta incorrecto");
                    world.setBlockState(at, world.getBlockState(at).with(DoorBlock.OPEN, true), Block.NOTIFY_ALL);
                    exige(Obra.hecha(world, cell), "Abrir la puerta invalida la casa");
                    world.setBlockState(at.up(), Blocks.AIR.getDefaultState(), CLEAN);
                    exige(!Obra.hecha(world, cell), "Puerta sin mitad superior contada como lista");
                    world.setBlockState(at, Blocks.AIR.getDefaultState(), CLEAN);
                    world.setBlockState(at.up(), Blocks.STONE.getDefaultState(), CLEAN);
                    exige(!Constructor.colocar(world, v, cell) && contar(v.getInventory(), m.door.asItem()) == 2, "Consume con el hueco superior bloqueado");
                }
            }
        } finally { v.discard(); }
    }

    private static void fabricacion(ServerWorld world) {
        BlockPos at = new BlockPos(21200, 100, 100);
        Pruebas.load(world, at, 12);
        Pruebas.fill(world, at.add(-4, -1, -4), at.add(4, -1, 4), Blocks.STONE.getDefaultState());
        Pruebas.fill(world, at.add(-4, 0, -4), at.add(4, 4, 4), Blocks.AIR.getDefaultState());
        world.setBlockState(at.east(), Blocks.CRAFTING_TABLE.getDefaultState(), Block.NOTIFY_ALL);
        VillagerEntityMCA v = Pruebas.villager(world, at, Direction.EAST, "Fabricación fiel");
        try {
            v.getInventory().addStack(new ItemStack(Items.OAK_LOG, 12));
            v.getInventory().addStack(new ItemStack(Items.OAK_PLANKS, 12));
            for (Taller.Receta r : List.of(Taller.Receta.TABLAS, Taller.Receta.ESCALERAS, Taller.Receta.PUERTA)) {
                exige(!Taller.hacer(v, r, Plano.Madera.ABETO, 0), "Fabricó la especie equivocada");
            }
            exige(contar(v.getInventory(), Items.OAK_LOG) == 12 && contar(v.getInventory(), Items.OAK_PLANKS) == 12, "Consumió los materiales rechazados");
            v.getInventory().addStack(new ItemStack(Items.SPRUCE_LOG, 3));
            exige(Taller.hacer(v, Taller.Receta.TABLAS, Plano.Madera.ABETO, 2), "No fabrica tablas exactas");
            exige(contar(v.getInventory(), Items.SPRUCE_LOG) == 2 && contar(v.getInventory(), Items.SPRUCE_PLANKS) == 4, "No respeta la reserva exacta");
            exige(!Taller.hacer(v, Taller.Receta.TABLAS, Plano.Madera.ABETO, 2), "Consumió los troncos reservados");
            v.getInventory().addStack(new ItemStack(Items.SPRUCE_PLANKS, 12));
            exige(Taller.hacer(v, Taller.Receta.ESCALERAS, Plano.Madera.ABETO, 0), "No fabrica escaleras exactas");
            exige(Taller.hacer(v, Taller.Receta.PUERTA, Plano.Madera.ABETO, 0), "No fabrica puerta exacta");
            exige(contar(v.getInventory(), Items.SPRUCE_STAIRS) == 4 && contar(v.getInventory(), Items.SPRUCE_DOOR) == 3, "Salida de otra especie");
            exige(contar(v.getInventory(), Items.OAK_LOG) == 12 && contar(v.getInventory(), Items.OAK_PLANKS) == 12, "Gastó roble para productos de abeto");
        } finally { v.discard(); }
    }

    private static final class Modelos extends Pruebas.Scenario {
        final List<Obra> obras = new ArrayList<>();
        final List<List<Obra.Celda>> ordenes = new ArrayList<>();
        final VillagerEntityMCA v;
        int casa, pieza, puestas;
        Modelos(ServerWorld world) {
            super("obra-modelos", world, 1000);
            int i = 0;
            for (CatalogoPlanos.Ficha f : CatalogoPlanos.fichas()) {
                BlockPos origin = new BlockPos(21500 + 30 * i, 100, 100);
                Pruebas.load(world, origin, 20);
                Pruebas.fill(world, origin.add(-8, -1, -8), origin.add(20, -1, 20), Blocks.STONE.getDefaultState());
                Pruebas.fill(world, origin.add(-8, 0, -8), origin.add(20, 16, 20), Blocks.AIR.getDefaultState());
                Plano.Madera m = Plano.Madera.values()[i++];
                Obra o = new Obra("prueba-casa-" + i, java.util.UUID.randomUUID(), "Prueba", origin, Direction.NORTH, m, CatalogoPlanos.plano(f.id(), m));
                obras.add(o);
                // Fixtures need their wall/roof supports; a y-sorted blueprint can list a torch before its wall.
                ordenes.add(o.celdas().stream().filter(c -> c.rol().bloque())
                        .sorted(java.util.Comparator.comparingInt(c -> c.rol().estructura ? 0 : 1)).toList());
            }
            v = Pruebas.villager(world, new BlockPos(21496, 100, 96), Direction.SOUTH, "Colocador de casas");
        }

        @Override Boolean step() {
            for (int batch = 0; batch < 8; batch++) {
                if (casa == obras.size()) {
                    Pruebas.check(name, true, "tres diseños con " + puestas + " objetos colocados por Constructor.colocar; posición asistida, inventario mixto");
                    return true;
                }
                Obra o = obras.get(casa);
                List<Obra.Celda> orden = ordenes.get(casa);
                if (pieza == orden.size()) {
                    for (Obra.Celda c : o.celdas()) {
                        exige(c.rol() == Plano.Rol.AIRE || Obra.hecha(world, c), "Casa final distinta del plano: " + c.pos());
                    }
                    casa++; pieza = 0; continue;
                }
                Obra.Celda c = orden.get(pieza++);
                Item required = Plano.item(c.rol(), c.state());
                v.getInventory().clear();
                v.getInventory().setStack(0, new ItemStack(o.madera == Plano.Madera.ROBLE ? Items.SPRUCE_LOG : Items.OAK_LOG, 64));
                v.getInventory().setStack(1, new ItemStack(o.madera == Plano.Madera.ROBLE ? Items.SPRUCE_PLANKS : Items.OAK_PLANKS, 64));
                v.getInventory().setStack(4, new ItemStack(required, 2));
                boolean placed = false;
                for (BlockPos candidate : BlockPos.iterateOutwards(c.pos(), 3, 2, 3)) {
                    if (o.ocupa(candidate) && !world.isAir(candidate) || o.ocupa(candidate.up()) && !world.isAir(candidate.up())) continue;
                    if (!world.getBlockState(candidate).isReplaceable() || !world.getBlockState(candidate.up()).isReplaceable()) continue;
                    BlockPos support = candidate.down();
                    boolean temporary = world.isAir(support) && !o.ocupa(support);
                    if (world.isAir(support) && !temporary) continue;
                    if (temporary) world.setBlockState(support, Blocks.DIRT.getDefaultState(), CLEAN);
                    v.refreshPositionAndAngles(candidate, 0, 0);
                    placed = Constructor.colocar(world, v, c);
                    if (temporary) world.setBlockState(support, Blocks.AIR.getDefaultState(), CLEAN);
                    if (placed) break;
                }
                exige(placed && Obra.hecha(world, c), "El colocador no puso " + c.rol() + " en " + c.pos());
                exige(v.getInventory().getStack(0).getCount() == 64 && v.getInventory().getStack(1).getCount() == 64
                        && v.getInventory().getStack(4).getCount() == 1, "Cambió el inventario equivocado");
                puestas++;
            }
            return null;
        }
        @Override String state() { return "casa " + casa + ", pieza " + pieza + ", colocadas " + puestas; }
        @Override void cleanup() { v.discard(); Constructor.olvidar(v.getUuid()); }
    }

    private static final class Madera extends Pruebas.Scenario {
        final VillagerEntityMCA v;
        final Progreso progreso = new Progreso();
        final BlockPos oak = new BlockPos(21700, 100, 100);
        final BlockPos spruce = oak.east(7);
        Madera(ServerWorld world) {
            super("obra-madera", world, 1800);
            Pruebas.load(world, oak, 20);
            Pruebas.fill(world, oak.add(-5, -1, -5), oak.add(14, -1, 5), Blocks.GRASS_BLOCK.getDefaultState());
            Pruebas.fill(world, oak.add(-5, 0, -5), oak.add(14, 10, 5), Blocks.AIR.getDefaultState());
            for (int y = 0; y < 3; y++) {
                world.setBlockState(oak.up(y), Blocks.OAK_LOG.getDefaultState(), CLEAN);
                world.setBlockState(spruce.up(y), Blocks.SPRUCE_LOG.getDefaultState(), CLEAN);
            }
            Pruebas.fill(world, oak.add(-1, 3, -1), oak.add(1, 4, 1), Blocks.OAK_LEAVES.getDefaultState());
            Pruebas.fill(world, spruce.add(-1, 3, -1), spruce.add(1, 4, 1), Blocks.SPRUCE_LEAVES.getDefaultState());
            v = Pruebas.villager(world, oak.east(2), Direction.EAST, "Busca abeto");
            v.getInventory().addStack(new ItemStack(Items.OAK_LOG, 16));
            v.getInventory().addStack(new ItemStack(Items.STONE_AXE));
            progreso.pedirTroncos(2, Items.SPRUCE_LOG);
        }
        @Override Boolean step() {
            for (BlockPos p : List.of(oak, spruce)) {
                Retina.memory(v).see(p, world.getBlockState(p), Direction.WEST, Vec3d.ofCenter(p), v.getEyePos().distanceTo(Vec3d.ofCenter(p)), 15, world.getTime());
            }
            progreso.tick(world, v, (key, args) -> {});
            if (Progreso.troncos(v, Items.SPRUCE_LOG) < 2) return null;
            Pruebas.check(name, world.getBlockState(oak).isOf(Blocks.OAK_LOG) && contar(v.getInventory(), Items.OAK_LOG) >= 16,
                    "abeto real talado/recogido; roble próximo conservado; abeto=" + Progreso.troncos(v, Items.SPRUCE_LOG));
            return true;
        }
        @Override String state() { return "abeto=" + Progreso.troncos(v, Items.SPRUCE_LOG) + ", roble=" + Progreso.troncos(v, Items.OAK_LOG); }
        @Override void cleanup() { progreso.olvidarPedidos(world, v); v.discard(); }
    }

    private static final class Escolta extends Pruebas.Scenario {
        final VillagerEntityMCA v;
        final Vec3d start;
        Escolta(ServerWorld world) {
            super("obra-escolta", world, 300);
            BlockPos o = new BlockPos(21900, 100, 100);
            Pruebas.load(world, o, 20);
            Pruebas.fill(world, o.add(-5, -1, -5), o.add(15, -1, 5), Blocks.GRASS_BLOCK.getDefaultState());
            Pruebas.fill(world, o.add(-5, 0, -5), o.add(15, 4, 5), Blocks.AIR.getDefaultState());
            v = Pruebas.villager(world, o, Direction.EAST, "Escolta real");
            start = v.getPos();
            var p = Pruebas.fakePlayer(world, "PedidoEscolta", Vec3d.ofBottomCenter(o.east(8)));
            TriggerCommandInfos.triggerCommands.stream().filter(c -> c.command.equals("start-escort")).findFirst().orElseThrow().run(p, v, "");
        }
        @Override Boolean step() {
            if (v.getPos().distanceTo(start) < 2) return null;
            Pruebas.check(name, v.getX() > start.x + 2, "start-escort por chat produjo desplazamiento real sin tarea ESCORT cancelada");
            return true;
        }
        @Override String state() { return "desplazamiento=" + v.getPos().distanceTo(start); }
        @Override void cleanup() { net.mca.entity.ai.mente.Voluntad.olvidar(v); v.discard(); }
    }
}
