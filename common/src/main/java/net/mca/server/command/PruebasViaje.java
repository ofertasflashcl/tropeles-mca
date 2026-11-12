package net.mca.server.command;

import net.mca.Config;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Chore;
import net.mca.entity.ai.MemoryModuleTypeMCA;
import net.mca.entity.ai.body.Hands;
import net.mca.entity.ai.body.Motor;
import net.mca.entity.ai.chatAI.ActionTurn;
import net.mca.entity.ai.chatAI.ChatAI;
import net.mca.entity.ai.chatAI.MenteLocal;
import net.mca.entity.ai.chatAI.OpenAIChatAI;
import net.mca.entity.ai.mente.Voluntad;
import net.mca.entity.ai.sense.Retina;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

final class PruebasViaje {
    private static final BlockPos ORIGEN = new BlockPos(35060, 100, 33000);
    private static final BlockPos PUNTO = ORIGEN.east(3);
    private static final String ALDEANO = "Nora Viaje";
    private static final String JUGADOR = "Viaje_CI";

    private PruebasViaje() {
    }

    static void run(ServerWorld world, String which) {
        Pruebas.start("viaje-reinicio".equals(which) ? new Reinicio(world) : new Viaje(world));
    }

    private static final class Viaje extends Pruebas.Scenario {
        private final VillagerEntityMCA v;
        private final ServerPlayerEntity p;
        private boolean conserva;
        private boolean observa;
        private boolean acompano;
        private boolean salto;
        private double anterior;
        private boolean guardar;

        Viaje(ServerWorld world) {
            super("viaje-companero", world, 420);
            Pruebas.load(world, ORIGEN, 24);
            Pruebas.fill(world, ORIGEN.add(-3, -1, -4), ORIGEN.add(20, -1, 4), Blocks.STONE.getDefaultState());
            Pruebas.fill(world, ORIGEN.add(-3, 0, -4), ORIGEN.add(20, 4, 4), Blocks.AIR.getDefaultState());
            v = Pruebas.villager(world, ORIGEN, Direction.EAST, ALDEANO);
            p = Pruebas.fakePlayer(world, JUGADOR, Vec3d.ofBottomCenter(PUNTO));
            v.getBrain().forget(MemoryModuleTypeMCA.STAYING.get());
            v.getVillagerBrain().assignJob(Chore.PROSPECT, p);
            v.setAiDisabled(true);
        }

        @Override
        Boolean step() {
            if (ticks == 10) {
                p.setOnGround(true);
                Voluntad.seguir(v, p, true);
                ActionTurn anteriorAlAcuerdo = ActionTurn.capture(p, v, 10_000);
                boolean vigente = anteriorAlAcuerdo.valid();
                boolean tools = Config.getInstance().villagerChatAIUseTools;
                Config.getInstance().villagerChatAIUseTools = true;
                try {
                    ChatAI.onPlayerChat(p, ALDEANO + ", nos juntamos aquí", System.currentTimeMillis());
                } finally {
                    Config.getInstance().villagerChatAIUseTools = tools;
                }
                var intruso = Pruebas.fakePlayer(world, "Otro_Viaje", Vec3d.ofBottomCenter(PUNTO));
                ServerWorld nether = world.getServer().getWorld(World.NETHER);
                conserva = vigente && !anteriorAlAcuerdo.valid() && PUNTO.equals(v.getPuntoEncuentro().para(world, p.getUuid()))
                        && v.getPuntoEncuentro().para(world, intruso.getUuid()) == null
                        && (nether == null || v.getPuntoEncuentro().para(nether, p.getUuid()) == null)
                        && "referencia-salida".equals(MenteLocal.read("Sube por detrás de él").question())
                        && "player-behind".equals(MenteLocal.read("Espera, mejor por detrás de mí").argument());
                comprobarMirada();
                Voluntad.seguir(v, p, true);
                anterior = v.getX();
                v.setAiDisabled(false);
            } else if (ticks < 65) {
                p.setPosition(PUNTO.getX() + 0.5 + (ticks - 10) * 0.17, PUNTO.getY(), PUNTO.getZ() + 0.5);
            } else if (ticks == 65) {
                acompano = v.getX() > PUNTO.getX() + 2;
                p.setPosition(ORIGEN.getX() + 62.5, ORIGEN.getY(), ORIGEN.getZ() + 0.5);
            }
            salto |= Math.abs(v.getX() - anterior) > 3 || v.getX() >= ORIGEN.getX() + 32;
            anterior = v.getX();
            if (ticks < 130 || !v.getBlockPos().isWithinDistance(PUNTO, 1.5)
                    || !Retina.memory(v).doing().contains("punto de reunión")) {
                return null;
            }
            guardar = conserva && observa && acompano && !salto;
            Pruebas.check(name, guardar, state());
            return guardar;
        }

        private void comprobarMirada() {
            BlockPos tramo = ORIGEN.east(5);
            BlockPos pared = ORIGEN.east(2).up();
            Hands.aim(v, Vec3d.ofCenter(tramo.up()));
            world.setBlockState(tramo.up(), Blocks.STONE.getDefaultState(), 3);
            boolean visto = tramo.equals(Motor.bloqueoVisible(world, v, List.of(tramo), true));
            world.setBlockState(pared, Blocks.STONE.getDefaultState(), 3);
            boolean oculto = Motor.bloqueoVisible(world, v, List.of(tramo), true) == null;
            world.setBlockState(pared, Blocks.AIR.getDefaultState(), 3);
            world.setBlockState(tramo.up(), Blocks.AIR.getDefaultState(), 3);
            observa = visto && oculto && Motor.bloqueoVisible(world, v, List.of(tramo), true) == null;
        }

        @Override
        String state() {
            return "acuerdo y turno vencido " + conserva + ", bloqueo visible y oclusión " + observa + ", acompañó " + acompano
                    + ", salto de posición " + salto + ", " + v.getBlockPos().toShortString() + ", " + Retina.memory(v).doing();
        }

        @Override
        void cleanup() {
            OpenAIChatAI.forget(v.getUuid());
            if (guardar) {
                v.setAiDisabled(true);
            } else {
                v.discard();
            }
        }
    }

    private static final class Reinicio extends Pruebas.Scenario {
        private VillagerEntityMCA encontrado;

        Reinicio(ServerWorld world) {
            super("viaje-reinicio", world, 220);
            Pruebas.load(world, PUNTO, 8);
        }

        @Override
        Boolean step() {
            var aldeanos = world.getEntitiesByClass(VillagerEntityMCA.class, new Box(PUNTO).expand(5),
                    v -> ALDEANO.equals(v.getName().getString()));
            if (aldeanos.isEmpty()) {
                return null;
            }
            encontrado = aldeanos.get(0);
            var p = Pruebas.fakePlayer(world, JUGADOR, Vec3d.ofBottomCenter(PUNTO));
            boolean ok = PUNTO.equals(encontrado.getPuntoEncuentro().para(world, p.getUuid()));
            Pruebas.check(name, ok, "punto de reunión recuperado del mundo guardado " + ok);
            return ok;
        }

        @Override
        String state() {
            return "esperando al compañero guardado en el punto de reunión";
        }

        @Override
        void cleanup() {
            if (encontrado != null) encontrado.discard();
        }
    }
}
