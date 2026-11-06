package net.mca.server.command;

import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.MemoryModuleTypeMCA;
import net.mca.entity.ai.brain.tasks.chore.ProspectingTask;
import net.mca.entity.ai.mente.Recuerdos;
import net.mca.entity.ai.mente.Voluntad;
import net.mca.entity.ai.sense.Obras;
import net.mca.entity.ai.sense.Retina;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class PruebasVidaViva {
    private PruebasVidaViva() {
    }

    static void run(ServerWorld world, String which) {
        Pruebas.start(switch (which) {
            case "cueva-autonoma" -> new Cueva(world);
            case "pan-autonomo" -> new Pan(world);
            case "cocina-autonoma" -> new Cocina(world);
            case "seguir-sin-spam" -> new Seguir(world);
            default -> throw new IllegalArgumentException(which);
        });
    }

    private abstract static class Vida extends Pruebas.Scenario {
        final ProspectingTask tarea = new ProspectingTask();
        final BlockPos o;
        VillagerEntityMCA v;

        Vida(String name, ServerWorld world, int limit, int x) {
            super(name, world, limit);
            o = new BlockPos(x, 100, 33000);
            Pruebas.load(world, o, 24);
        }

        void campo() {
            Pruebas.fill(world, o.add(-10, -2, -10), o.add(10, -1, 10), Blocks.GRASS_BLOCK.getDefaultState());
            Pruebas.fill(world, o.add(-10, 0, -10), o.add(10, 6, 10), Blocks.AIR.getDefaultState());
        }

        void aldeano(String name) {
            v = Pruebas.villager(world, o, Direction.EAST, name);
            v.getBrain().forget(MemoryModuleTypeMCA.STAYING.get());
            v.getInventory().addStack(new ItemStack(Items.STONE_PICKAXE));
        }

        void buscarComida() {
            if (ticks == 10) {
                tarea.setFood(4);
                tarea.buscarComida();
                tarea.retomar(world, v);
            }
            tarea.practice(world, v);
        }

        boolean comprobar(boolean ok, String detail) {
            Pruebas.check(name, ok, detail + " en " + ticks + " ticks");
            return ok;
        }

        @Override
        String state() {
            return v.getBlockPos().toShortString() + ", " + tarea.debug() + ", " + Retina.memory(v).doing()
                    + ", comida " + tarea.food();
        }

        @Override
        void cleanup() {
            Voluntad.olvidar(v);
            v.discard();
        }
    }

    private static final class Cueva extends Vida {
        private final BlockPos mena;
        private final BlockPos tunel;
        private boolean entro;

        Cueva(ServerWorld world) {
            super("cueva-autonoma", world, 1800, 33500);
            Pruebas.fill(world, o.add(-5, -7, -5), o.add(9, 5, 9), Blocks.STONE.getDefaultState());
            Pruebas.fill(world, o.west(3), o.up(), Blocks.AIR.getDefaultState());
            Pruebas.fill(world, o.south(), o.add(1, 2, 4), Blocks.CAVE_AIR.getDefaultState());
            world.setBlockState(o.add(0, 3, 3), Blocks.GLOWSTONE.getDefaultState(), 3);
            mena = o.south(5).up();
            tunel = o.east();
            world.setBlockState(mena, Blocks.DIAMOND_ORE.getDefaultState(), 3);
            aldeano("Ada Cueva");
            v.getInventory().clear();
            v.getInventory().addStack(new ItemStack(Items.IRON_PICKAXE));
            v.getInventory().addStack(new ItemStack(Items.TORCH, 12));
        }

        @Override
        Boolean step() {
            tarea.practice(world, v);
            entro |= v.getBlockZ() >= o.getZ() + 2;
            if (world.getBlockState(tunel).isOf(Blocks.STONE) || !entro || v.getInventory().count(Items.DIAMOND) == 0) {
                return null;
            }
            var memoria = Recuerdos.of(world, v);
            boolean volvio = memoria.hechos("exploracion", 8).stream().anyMatch(h -> o.equals(h.donde()) && h.texto().contains("volví por mis pasos"));
            return comprobar(volvio && memoria.sitio("cueva") != null, "minó el diamante, regresó al punto de partida y reanudó el túnel");
        }

        @Override
        String state() {
            return super.state() + ", entró " + entro + ", mena " + world.getBlockState(mena) + ", frente " + world.getBlockState(tunel);
        }
    }

    private static final class Pan extends Vida {
        private final List<BlockPos> trigo = new ArrayList<>();
        private final BlockPos privado;

        Pan(ServerWorld world) {
            super("pan-autonomo", world, 900, 33620);
            campo();
            aldeano("Eva Pan");
            v.getInventory().addStack(new ItemStack(Items.WHEAT_SEEDS, 8));
            v.getInventory().addStack(new ItemStack(Items.CRAFTING_TABLE));
            for (int z = -1; z <= 1; z++) {
                BlockPos at = o.add(2, 0, z);
                plantar(at);
                trigo.add(at);
            }
            privado = o.east();
            plantar(privado);
            Obras.placedByPlayer(world, privado.down());
            Retina.lookAll(world, v);
        }

        private void plantar(BlockPos at) {
            world.setBlockState(at.down(), Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE, 7), 2);
            world.setBlockState(at, Blocks.WHEAT.getDefaultState().with(CropBlock.AGE, 7), 2);
        }

        @Override
        Boolean step() {
            buscarComida();
            boolean hizoPan = v.getInventory().count(Items.BREAD) > 0 || tarea.food() >= 9;
            if (!hizoPan) {
                return null;
            }
            boolean replantado = trigo.stream().allMatch(at -> {
                BlockState s = world.getBlockState(at);
                return s.isOf(Blocks.WHEAT) && s.get(CropBlock.AGE) < 7;
            });
            boolean intacto = world.getBlockState(privado).isOf(Blocks.WHEAT) && world.getBlockState(privado).get(CropBlock.AGE) == 7;
            return comprobar(replantado && intacto, "pan hecho con trigo cosechado, tres plantas resembradas " + replantado + ", cultivo del jugador intacto " + intacto);
        }

        @Override
        void cleanup() {
            net.mca.server.world.data.TropelesMundo.get(world).removed(privado.down());
            super.cleanup();
        }
    }

    private static final class Cocina extends Vida {
        private final PigEntity cerdo;
        private boolean cocino;

        Cocina(ServerWorld world) {
            super("cocina-autonoma", world, 1500, 33740);
            campo();
            Pruebas.fill(world, o.add(4, 0, -2), o.add(5, 2, 2), Blocks.STONE.getDefaultState());
            aldeano("Ina Horno");
            v.getInventory().addStack(new ItemStack(Items.STONE_SWORD));
            v.getInventory().addStack(new ItemStack(Items.CRAFTING_TABLE));
            v.getInventory().addStack(new ItemStack(Items.COAL, 4));
            cerdo = Objects.requireNonNull(EntityType.PIG.create(world));
            Vec3d at = Vec3d.ofBottomCenter(o.east(2));
            cerdo.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
            cerdo.setAiDisabled(true);
            cerdo.setPersistent();
            world.spawnEntity(cerdo);
            Retina.lookAll(world, v);
        }

        @Override
        Boolean step() {
            buscarComida();
            cocino |= tarea.cooking();
            boolean cocida = v.getInventory().count(Items.COOKED_PORKCHOP) > 0 || tarea.food() >= 12;
            if (!cocida) {
                return null;
            }
            return comprobar(cerdo.isDead() && cocino, "cazó, consiguió piedra, fabricó el horno y cocinó");
        }

        @Override
        String state() {
            return super.state() + ", cerdo vivo " + cerdo.isAlive() + ", cocinó " + cocino + ", carne "
                    + v.getInventory().count(Items.PORKCHOP) + ", roca " + v.getInventory().count(Items.COBBLESTONE);
        }

        @Override
        void cleanup() {
            cerdo.discard();
            super.cleanup();
        }
    }

    private static final class Seguir extends Vida {
        private final ServerPlayerEntity p;
        private final BlockPos mena;
        private int copio;

        Seguir(ServerWorld world) {
            super("seguir-sin-spam", world, 2400, 33860);
            campo();
            aldeano("Luz Sigue");
            p = Pruebas.fakePlayer(world, "Shift_CI", Vec3d.ofBottomCenter(o.east(2)));
            mena = o.east(5);
            world.setBlockState(mena, Blocks.COAL_ORE.getDefaultState(), 2);
            Voluntad.seguir(v, p, true);
        }

        @Override
        Boolean step() {
            boolean pulsa = (ticks >= 10 && ticks < 24 || ticks >= 200 && ticks < 216) && ticks % 2 == 0;
            p.setSneaking(pulsa);
            if (ticks == 32) {
                Voluntad.oyo(v, p, true);
            }
            tarea.practice(world, v);
            if (pulsa && v.isSneaking()) {
                copio++;
            }
            if (Voluntad.of(v).modo() != Voluntad.Modo.LIBRE || world.getBlockState(mena).isOf(Blocks.COAL_ORE)) {
                return null;
            }
            var dichos = Recuerdos.of(world, v).hechos("dijo", 30);
            long preguntas = dichos.stream().filter(h -> List.of("¿Qué quieres hacer?", "¿Sigues ahí?", "¿Vamos a algún sitio?",
                    "¿Necesitas ayuda con algo?").stream().anyMatch(h.texto()::contains)).count();
            boolean aviso = dichos.stream().anyMatch(h -> h.texto().contains("Voy a empezar por mi cuenta"));
            return comprobar(copio > 0 && copio <= 2 && preguntas == 9 && aviso,
                    "imitaciones " + copio + ", preguntas " + preguntas + " (una contestada y ocho sin respuesta), aviso " + aviso + ", volvió a minar");
        }
    }
}
