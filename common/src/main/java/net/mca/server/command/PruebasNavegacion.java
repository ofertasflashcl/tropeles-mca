package net.mca.server.command;

import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.body.Mina;
import net.mca.entity.ai.body.Motor;
import net.mca.entity.ai.mente.Recuerdos;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.UUID;
import java.util.function.Predicate;

/** Regression checks against real server geometry and the real recorder/search, not a copy of their algorithms. */
final class PruebasNavegacion {
    private static final UUID SAVED_MINE = UUID.fromString("00000000-0000-0000-0000-000000c13704");

    private PruebasNavegacion() {
    }

    static void run(ServerWorld world, String which) {
        if (which.equals("mina-rastro-reinicio")) {
            Mina saved = Mina.load(world, SAVED_MINE);
            boolean passed = saved != null && saved.size() == 1500 && saved.entrance() != null
                    && saved.entrance().equals(new BlockPos(27000, 70, 0)) && saved.last().equals(new BlockPos(28499, 70, 0));
            for (int i = 0; passed && i < 1500; i++) {
                passed = saved.crumb(i).equals(new BlockPos(27000 + i, 70, 0));
            }
            Pruebas.check(which, passed, "leyó sin recrear 1500 pasos y la entrada en otro proceso del mismo mundo: " + passed);
            return;
        }
        if (which.equals("mina-rastro")) {
            rastro(world);
            return;
        }
        BlockPos origin = new BlockPos(switch (which) {
            case "ruta-presupuesto" -> 25200;
            case "ruta-transicion" -> 25300;
            default -> 25400;
        }, 100, 0);
        Pruebas.load(world, origin.add(20, 0, 0), 24);
        Pruebas.fill(world, origin.add(-2, -1, -2), origin.add(42, -1, 4), Blocks.STONE.getDefaultState());
        Pruebas.fill(world, origin.add(-2, 0, -2), origin.add(42, 4, 4), Blocks.AIR.getDefaultState());
        VillagerEntityMCA v = Pruebas.villager(world, origin, Direction.EAST, "Navegacion");
        try {
            switch (which) {
                case "ruta-presupuesto" -> presupuesto(world, origin);
                case "ruta-transicion" -> transicion(world, v, origin);
                case "coste-rotura" -> coste(world, v, origin);
                default -> throw new IllegalArgumentException(which);
            }
        } finally {
            v.discard();
        }
    }

    private static void rastro(ServerWorld world) {
        long[] cells = new long[1500];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = BlockPos.asLong(27000 + i, 70, 0);
        }
        NbtCompound old = new NbtCompound();
        old.putLongArray("c", cells);
        old.putBoolean("door", true);
        Mina mine = Mina.fromNbt(old);
        UUID id = SAVED_MINE;
        mine.save(world, id);
        Mina restored = Mina.load(world, id);
        boolean kept = restored != null && restored.size() == cells.length && BlockPos.fromLong(cells[0]).equals(restored.entrance())
                && restored.last().equals(BlockPos.fromLong(cells[cells.length - 1]));
        boolean everyStep = kept;
        for (int i = 0; everyStep && i < cells.length; i++) {
            everyStep = restored.crumb(i).asLong() == cells[i];
        }
        // At capacity it keeps the complete prefix and requests a return, instead of dropping the entrance.
        long[] full = new long[Mina.MAX + 10];
        for (int i = 0; i < full.length; i++) {
            full[i] = BlockPos.asLong(30000 + i, 70, 0);
        }
        old.putLongArray("c", full);
        Mina capped = Mina.fromNbt(old);
        boolean protectedRoute = capped.size() == Mina.MAX && capped.needsReturn()
                && BlockPos.fromLong(full[0]).equals(capped.entrance())
                && capped.last().equals(BlockPos.fromLong(full[Mina.MAX - 1]));
        Pruebas.check("mina-rastro", everyStep && protectedRoute,
                "1500 pasos y boca conservados al serializar/cargar: " + everyStep + ", límite sin perder la subida: " + protectedRoute);
    }

    private static void presupuesto(ServerWorld world, BlockPos origin) {
        BlockPos goal = origin.east(32);
        Predicate<BlockPos> known = p -> p.getY() == origin.getY() && p.getZ() == origin.getZ()
                && p.getX() >= origin.getX() && p.getX() <= goal.getX();
        Motor.WalkSearch search = new Motor.WalkSearch(origin, goal, 0.1);
        Motor.WalkResult first = search.advance(world, 1, known, null);
        Motor.WalkResult result = first;
        int slices = 1;
        while (result.status() == Motor.WalkStatus.BUDGET_EXHAUSTED && slices++ < 100) {
            result = search.advance(world, 1, known, null);
        }
        Motor.WalkResult noPath = new Motor.WalkSearch(origin, goal, 0.1).advance(world, 10, known, (a, b) -> false);
        boolean passed = first.status() == Motor.WalkStatus.BUDGET_EXHAUSTED && result.status() == Motor.WalkStatus.FOUND
                && result.path().size() == 33 && result.path().get(0).equals(origin) && result.path().get(32).equals(goal)
                && noPath.status() == Motor.WalkStatus.NO_PATH;
        Pruebas.check("ruta-presupuesto", passed, "primero " + first.status() + ", después " + result.status()
                + " con " + result.expanded() + " nodos en " + slices + " cuotas; sin conexión: " + noPath.status());
    }

    private static void transicion(ServerWorld world, VillagerEntityMCA v, BlockPos origin) {
        Recuerdos memory = Recuerdos.of(world, v);
        BlockPos next = origin.east();
        BlockPos goal = origin.east(5);
        long now = world.getTime();
        memory.cerrarPaso(origin, next, now + 40);
        Predicate<BlockPos> known = p -> p.getY() == origin.getY() && p.getX() >= origin.getX() && p.getX() <= goal.getX()
                && p.getZ() >= origin.getZ() && p.getZ() <= origin.getZ() + 1;
        Motor.WalkResult result = new Motor.WalkSearch(origin, goal, 0.1).advance(world, 200, known,
                (a, b) -> !memory.pasoCerrado(a, b, now));
        Motor.WalkResult reverse = new Motor.WalkSearch(next, origin, 0.1).advance(world, 200, known,
                (a, b) -> !memory.pasoCerrado(a, b, now));
        boolean detour = result.status() == Motor.WalkStatus.FOUND && !result.path().get(1).equals(next);
        boolean passed = detour && !memory.cerrado(next, now) && !memory.pasoCerrado(next, origin, now)
                && reverse.status() == Motor.WalkStatus.FOUND && reverse.path().size() == 2
                && !memory.pasoCerrado(origin, next, now + 40);
        Pruebas.check("ruta-transicion", passed, "rodea el intento fallido: " + detour + ", destino abierto y vuelta directa: "
                + reverse.status() + ", no veta otras aproximaciones y caduca");
    }

    private static void coste(ServerWorld world, VillagerEntityMCA v, BlockPos origin) {
        BlockPos stone = origin.east(2);
        world.setBlockState(stone, Blocks.STONE.getDefaultState(), 3);
        v.getInventory().clear();
        v.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, ItemStack.EMPTY);
        v.setOnGround(true);
        int hand = Motor.breakingCost(world, v, stone);
        ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
        v.getInventory().setStack(0, pick);
        int equipped = Motor.breakingCost(world, v, stone);
        boolean unchanged = pick.getDamage() == 0 && pick.getCount() == 1 && v.getMainHandStack().isEmpty();
        Pruebas.check("coste-rotura", hand > equipped * 5 && unchanged,
                "piedra: mano " + hand + " ticks, pico disponible " + equipped + "; calcular no equipa ni consume: " + unchanged);
    }
}
