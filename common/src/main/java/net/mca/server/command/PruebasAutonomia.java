package net.mca.server.command;

import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.LongTermMemory;
import net.mca.entity.ai.MemoryModuleTypeMCA;
import net.mca.entity.ai.body.Mina;
import net.mca.entity.ai.body.Mining;
import net.mca.entity.ai.chatAI.PruebasRespuesta;
import net.mca.entity.ai.brain.tasks.chore.ProspectingTask;
import net.mca.entity.ai.mente.PruebasRecuerdos;
import net.mca.entity.ai.nucleo.PruebasIncidentes;
import net.mca.server.world.data.PruebasCronica;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Real disk, actual memory objects, actual mine recorder and actual returning job. */
final class PruebasAutonomia {
    @FunctionalInterface private interface Check { String run() throws Exception; }
    private static void check(String name, Check check) {
        try { Pruebas.check(name, true, check.run()); }
        catch (Exception e) { Pruebas.check(name, false, e.toString()); }
    }

    static void run(ServerWorld world, String which) {
        if (which.equals("autonomia-reinicio")) {
            check("memoria-cronica-reinicio", () -> PruebasRecuerdos.cronica(world, false));
            return;
        }
        check("memoria-cronica", () -> PruebasRecuerdos.cronica(world, true));
        check("memoria-transaccion", () -> PruebasCronica.run(world));
        check("memoria-incidentes", PruebasIncidentes::run);
        check("memoria-permanente", () -> permanente(world));
        check("red-respuesta-acotada", PruebasRespuesta::run);
        check("mina-pies", () -> pies(world));
        Pruebas.start(new Vuelta(world));
        Pruebas.start(new Vuelta(world, true));
    }

    private static void require(boolean ok, String why) { if (!ok) throw new IllegalStateException(why); }

    private static String permanente(ServerWorld world) {
        BlockPos o = new BlockPos(35800, 100, 0);
        Pruebas.load(world, o, 4);
        VillagerEntityMCA v = Pruebas.villager(world, o, Direction.NORTH, "Memoria permanente");
        try {
            LongTermMemory memory = new LongTermMemory(v);
            memory.remember("permanente");
            memory.remember("permanente", 20);
            memory.remember("temporal", 40);
            require(memory.getMemory("permanente") == Long.MAX_VALUE && memory.getMemory("temporal") == 40, "Permanente/temporal incorrecto");
            NbtCompound stored = new NbtCompound(); memory.writeToNbt(stored);
            stored.getCompound("longTermMemory").putLong("cerca-del-limite", Long.MAX_VALUE - 2);
            LongTermMemory restored = new LongTermMemory(v); restored.readFromNbt(stored);
            restored.remember("cerca-del-limite", 10);
            require(restored.hasMemory("permanente") && restored.getMemory("cerca-del-limite") == Long.MAX_VALUE, "Perdio recuerdo o desbordo el vencimiento");
            restored.remember("caduca-ahora", 0);
            require(!restored.hasMemory("caduca-ahora"), "Vencimiento cero no caduco");
            return "permanente sin plazo finito; NBT, vencimiento temporal y suma sin overflow comprobados";
        } finally { v.discard(); }
    }

    private static String pies(ServerWorld world) {
        BlockPos o = new BlockPos(36000, 100, 0);
        Pruebas.load(world, o, 6);
        Pruebas.fill(world, o.add(-2, -1, -2), o.add(5, -1, 2), Blocks.STONE.getDefaultState());
        Pruebas.fill(world, o.add(-2, 0, -2), o.add(5, 4, 2), Blocks.AIR.getDefaultState());
        world.setBlockState(o, Blocks.OAK_SLAB.getDefaultState().with(Properties.SLAB_TYPE, SlabType.BOTTOM), 3);
        world.setBlockState(o.east(), Blocks.OAK_SLAB.getDefaultState().with(Properties.SLAB_TYPE, SlabType.BOTTOM), 3);
        world.setBlockState(o.east(2), Blocks.OAK_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST), 3);
        VillagerEntityMCA v = Pruebas.villager(world, o, Direction.EAST, "Pies sobre escalones");
        try {
            v.refreshPositionAndAngles(o.getX() + 0.5, o.getY() + 0.5, o.getZ() + 0.5, 0, 0); v.setOnGround(true);
            Mina mine = new Mina();
            require(mine.outside(world, v) && o.up().equals(mine.entrance()), "Omitio entrada sobre losa");
            v.refreshPositionAndAngles(o.getX() + 1.5, o.getY() + 0.5, o.getZ() + 0.5, 0, 0); v.setOnGround(true);
            require(mine.walked(world, v) && o.east().up().equals(mine.last()), "Omitio paso sobre losa");
            v.refreshPositionAndAngles(o.getX() + 2.2, o.getY() + 0.5, o.getZ() + 0.5, 0, 0); v.setOnGround(true);
            require(Mining.pies(v).equals(o.east(2).up()) && mine.walked(world, v), "Omitio peldano bajo de escalera");
            Mina restored = Mina.fromNbt(mine.toNbt());
            require(restored.size() == 3 && restored.last().equals(o.east(2).up()), "Cambio el rastro al guardarlo");
            return "entrada y tres apoyos sobre losas/escalera guardados con los mismos pies que el navegador";
        } finally { v.discard(); }
    }

    private static final class Vuelta extends Pruebas.Scenario {
        final VillagerEntityMCA v;
        final BlockPos door;
        final BlockPos face;
        final boolean blocked;
        final Map<BlockPos, BlockState> guards = new LinkedHashMap<>();
        int waiting;

        Vuelta(ServerWorld world) {
            this(world, false);
        }

        Vuelta(ServerWorld world, boolean blocked) {
            super(blocked ? "mina-ruta-cerrada" : "mina-retorno-cuota", world, blocked ? 1200 : 2600);
            this.blocked = blocked;
            door = new BlockPos(blocked ? 37000 : 36400, 100, 0);
            face = door.add(140, -6, 0);
            Pruebas.load(world, door.east(70), 88);
            Pruebas.fill(world, door.add(-5, -12, -3), door.add(145, -2, 3), Blocks.STONE.getDefaultState());
            Pruebas.fill(world, door.add(-5, -1, -3), door.add(145, -1, 3), Blocks.GRASS_BLOCK.getDefaultState());
            Pruebas.fill(world, door.add(-5, 0, -3), door.add(145, 5, 3), Blocks.AIR.getDefaultState());
            var trail = new ArrayList<BlockPos>(); trail.add(door);
            for (int x = 1; x <= 140; x++) {
                BlockPos c = door.add(x, -Math.min(6, x), 0);
                Pruebas.fill(world, c, c.up(x <= 6 ? 2 : 1), Blocks.AIR.getDefaultState());
                if (x > 6 && x % 4 == 3) world.setBlockState(c.up(2), Blocks.GLOWSTONE.getDefaultState(), 3);
                trail.add(c);
            }
            if (blocked) Pruebas.fill(world, door.add(20, -6, 0), door.add(20, -4, 0), Blocks.STONE.getDefaultState());
            if (blocked) {
                // Bound this negative fixture's reachable surface: exhausting a large open plain at one node/tick
                // would test only its timeout, not termination of a genuinely completed NO_PATH answer.
                Pruebas.fill(world, door.add(-1, 0, -1), door.add(-1, 2, 1), Blocks.STONE.getDefaultState());
                Pruebas.fill(world, door.add(0, -6, -1), door.add(7, 2, -1), Blocks.STONE.getDefaultState());
                Pruebas.fill(world, door.add(0, -6, 1), door.add(7, 2, 1), Blocks.STONE.getDefaultState());
                Pruebas.fill(world, door.add(7, 0, -1), door.add(7, 2, 1), Blocks.STONE.getDefaultState());
            }
            for (BlockPos c : trail) {
                for (BlockPos p : new BlockPos[]{c.down(), c, c.up(), c.up(2), c.north(), c.south()}) guards.put(p, world.getBlockState(p));
            }
            v = Pruebas.villager(world, door, Direction.EAST, "Regreso con cuota");
            v.getBrain().forget(MemoryModuleTypeMCA.STAYING.get());
            v.getInventory().addStack(new ItemStack(Items.IRON_PICKAXE));
            v.getInventory().addStack(new ItemStack(Items.IRON_SWORD));
            v.getInventory().addStack(new ItemStack(Items.BREAD, 32));
            v.getInventory().addStack(new ItemStack(Items.TORCH, 32));
            NbtCompound saved = new NbtCompound(); saved.putLongArray("c", trail.stream().mapToLong(BlockPos::asLong).toArray()); saved.putBoolean("door", true);
            Mina.fromNbt(saved).save(world, v.getUuid());
            var player = Pruebas.fakePlayer(world, "PedidoVueltaCuota", Vec3d.ofBottomCenter(door.west(3)));
            v.getVillagerBrain().assignJob(net.mca.entity.ai.Chore.PROSPECT, player);
            ProspectingTask task = ProspectingTask.of(v); task.setWalkBudget(1); task.setFood(20); task.setNoche(false); task.setHome(door.west(3));
        }

        @Override Boolean step() {
            ProspectingTask task = ProspectingTask.of(v); task.setWalkBudget(1);
            boolean pending = task.debug().contains("búsqueda pendiente");
            if (pending) waiting++;
            if (blocked && waiting > 0 && !pending && task.fase().equals("RETURN") && !task.porMemoria()) {
                require(door.equals(task.minaEntrada()), "Olvido la entrada mientras buscaba");
                Pruebas.check(name, true, "una ruta realmente cerrada termina la espera pendiente; cuota 1, espera=" + waiting);
                return true;
            }
            if (ticks % 10 == 0 || v.getPos().distanceTo(Vec3d.ofBottomCenter(face)) < 2.5) {
                for (var e : guards.entrySet()) require(world.getBlockState(e.getKey()).equals(e.getValue()), "Rompio/coloco en el corredor durante la vuelta: " + e.getKey());
            }
            require(v.isAlive(), "Murio volviendo por un corredor abierto");
            if (v.getPos().distanceTo(Vec3d.ofBottomCenter(face)) >= 2.5) return null;
            require(waiting >= 30 && door.equals(task.minaEntrada()), "No continuo la busqueda con cuota o perdio la entrada; espera=" + waiting);
            Pruebas.check(name, true, "volvio fisicamente por corredor de 140 bloques; cuota 1, espera=" + waiting + " ticks, cero bloques alterados");
            return true;
        }
        @Override String state() { return ProspectingTask.of(v).debug() + ", espera=" + waiting + ", x=" + v.getX(); }
        @Override void cleanup() { v.getVillagerBrain().abandonJob(); v.discard(); }
    }
}
