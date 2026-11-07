package net.mca.server.command;

import net.mca.Config;
import net.mca.MCA;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Chore;
import net.mca.entity.ai.MemoryModuleTypeMCA;
import net.mca.entity.ai.brain.tasks.chore.ProspectingTask;
import net.mca.entity.ai.body.Hands;
import net.mca.entity.ai.body.Motor;
import net.mca.entity.ai.body.RescateAgua;
import net.mca.entity.ai.body.Toques;
import net.mca.entity.ai.chatAI.Cerebros;
import net.mca.entity.ai.chatAI.MenteLocal;
import net.mca.entity.ai.chatAI.OpenAIChatAI;
import net.mca.entity.ai.mente.Recuerdos;
import net.mca.entity.ai.mente.Voluntad;
import net.mca.entity.ai.sense.Retina;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

final class PruebasVistaCerebro {
    private PruebasVistaCerebro() {
    }

    static void run(ServerWorld world, String which) {
        switch (which) {
            case "vista-accion" -> Pruebas.start(new Vista(world));
            case "subida-detras" -> Pruebas.start(new Subida(world));
            case "agua-exploracion" -> Pruebas.start(new Agua(world));
            case "agua-respiradero" -> Pruebas.start(new AguaTunel(world));
            case "agua-profunda" -> Pruebas.start(new AguaProfunda(world));
            default -> throw new IllegalArgumentException(which);
        }
    }

    private static final class Vista extends Pruebas.Scenario {
        private final BlockPos oro;
        private final BlockPos oculto;
        private final VillagerEntityMCA v;
        private final ServerPlayerEntity p;

        Vista(ServerWorld world) {
            super("vista-accion", world, 80);
            BlockPos o = new BlockPos(34000, 100, 33000);
            oro = o.east(3).up();
            oculto = o.east(4).up();
            Pruebas.load(world, o, 20);
            Pruebas.fill(world, o.add(-6, -1, -6), o.add(10, -1, 6), Blocks.STONE.getDefaultState());
            Pruebas.fill(world, o.add(-6, 0, -6), o.add(10, 6, 6), Blocks.AIR.getDefaultState());
            Pruebas.fill(world, o.add(3, 0, -5), o.add(5, 4, 5), Blocks.STONE.getDefaultState());
            world.setBlockState(oro, Blocks.GOLD_BLOCK.getDefaultState(), 3);
            world.setBlockState(oculto, Blocks.DIAMOND_BLOCK.getDefaultState(), 3);
            world.setBlockState(oro.south(), Blocks.IRON_ORE.getDefaultState(), 3);
            v = Pruebas.villager(world, o, Direction.EAST, "Paz Vista");
            p = Pruebas.fakePlayer(world, "Vista_CI", Vec3d.ofBottomCenter(o.west(2)));
            v.setAiDisabled(true);
        }

        @Override
        Boolean step() {
            if (world.getEntity(v.getUuid()) != v) {
                return null;
            }
            Config config = Config.getInstance();
            boolean herramientas = config.villagerChatAIUseTools;
            boolean vision = config.tropelesChatVision;
            config.villagerChatAIUseTools = true;
            config.tropelesChatVision = true;
            try {
                v.getVillagerBrain().assignJob(Chore.PROSPECT, p);
                Hands.aim(v, Vec3d.ofCenter(oro));
                boolean[] obsoleto = {false};
                int[] fotos = {0};
                OpenAIChatAI modelo = new OpenAIChatAI() {
                    @Override
                    protected Cerebros.Reply askMinds(List<Cerebros.Mind> minds, Function<Cerebros.Mind, String> sistema,
                                                     List<Pair<String, String>> historia, String msg, String jugador, String aldeano,
                                                     UUID id, long plazo, String imagen) {
                        Cerebros.Mind prueba = new Cerebros.Mind("prueba", "", "", "", true, false, java.util.Map.of(), null);
                        String contexto = sistema.apply(prueba);
                        var ref = Pattern.compile("(?m)^(b[0-9]+) = minecraft:gold_block at ").matcher(contexto);
                        if (imagen == null || !imagen.startsWith("data:image/png;base64,iVBOR") || !ref.find()
                                || contexto.contains("minecraft:diamond_block") || !contexto.contains("inspect-view")) {
                            throw new IllegalStateException("La muestra no representa los rayos de ese momento");
                        }
                        fotos[0]++;
                        if (obsoleto[0]) {
                            world.setBlockState(oro, Blocks.STONE.getDefaultState(), 3);
                        }
                        return new Cerebros.Reply("prueba", "{\"message\":\"Es diamante.\",\"optionalCommand\":\"inspect-view\",\"argument\":\""
                                + ref.group(1) + "\"}", 0, true);
                    }
                };
                String visto = modelo.answer(p, v, "Revisa ese bloque que tienes delante, por favor.").orElse("");
                obsoleto[0] = true;
                String cambio = modelo.answer(p, v, "Comprueba de nuevo ese bloque, por favor.").orElse("");
                var hechos = Recuerdos.of(world, v).hechos("vista", 8);
                boolean oroReal = hechos.size() == 1 && oro.equals(hechos.get(0).donde()) && hechos.get(0).texto().contains("minecraft:gold_block");
                boolean exacto = visto.contains(oro.getX() + " " + oro.getY() + " " + oro.getZ()) && !visto.contains("diamante");
                var hallazgos = Recuerdos.of(world, v).hechos("vista-hallazgo", 8);
                boolean hierroVisto = hallazgos.stream().anyMatch(h -> oro.south().equals(h.donde()) && h.texto().contains("minecraft:iron_ore"));
                boolean mochila = "bag".equals(MenteLocal.read("¿Qué tienes en la mochila?").question());
                boolean ok = fotos[0] == 2 && oroReal && exacto && hierroVisto && mochila && cambio.contains("ya cambió") && Retina.memory(v).stateAt(oculto) == null;
                Pruebas.check(name, ok, "muestras " + fotos[0] + ", memoria " + oroReal + ", mineral visible guardado " + hierroVisto
                        + ", distingue mochila " + mochila + ", respuesta " + visto + ", cambio " + cambio);
                return ok;
            } finally {
                config.villagerChatAIUseTools = herramientas;
                config.tropelesChatVision = vision;
            }
        }

        @Override
        String state() {
            return v.getBlockPos().toShortString() + ", registrado " + (world.getEntity(v.getUuid()) == v);
        }

        @Override
        void cleanup() {
            OpenAIChatAI.forget(v.getUuid());
            Voluntad.olvidar(v);
            v.discard();
        }
    }

    private static final class Subida extends Pruebas.Scenario {
        private final BlockPos o = new BlockPos(34120, 96, 33000);
        private final VillagerEntityMCA v;
        private final ServerPlayerEntity p;
        private boolean miroDetras;
        private int roturas;
        private String respuesta = "";

        Subida(ServerWorld world) {
            super("subida-detras", world, 550);
            Pruebas.load(world, o, 24);
            Pruebas.fill(world, o.add(-13, -6, -8), o.add(13, 3, 8), Blocks.STONE.getDefaultState());
            Pruebas.fill(world, o.add(-13, 4, -8), o.add(13, 10, 8), Blocks.AIR.getDefaultState());
            for (int k = 0; k <= 4; k++) {
                BlockPos celda = o.west(k).up(k);
                Pruebas.fill(world, celda, celda.up(2), Blocks.AIR.getDefaultState());
                world.setBlockState(celda.north(), Blocks.GLOWSTONE.getDefaultState(), 3);
                if (k < 4) {
                    world.setBlockState(celda.up(3), Blocks.STONE.getDefaultState(), 3);
                }
            }
            v = Pruebas.villager(world, o, Direction.EAST, "Luz Subida");
            v.getBrain().forget(MemoryModuleTypeMCA.STAYING.get());
            p = Pruebas.fakePlayer(world, "Subida_CI", Vec3d.ofBottomCenter(o.west(7).up(4)));
            v.getVillagerBrain().assignJob(Chore.PROSPECT, p);
            v.setAiDisabled(true);
            Toques.escuchar(v, (pos, colocado, now) -> {
                if (!colocado) {
                    roturas++;
                }
            });
        }

        @Override
        Boolean step() {
            if (ticks == 10) {
                Hands.aim(v, v.getEyePos().add(3, 0, 0));
                boolean herramientas = Config.getInstance().villagerChatAIUseTools;
                Config.getInstance().villagerChatAIUseTools = true;
                Cerebros.OFFLINE.set(true);
                try {
                    respuesta = new OpenAIChatAI().answer(p, v, "Intenta subir por detrás de ti.").orElse("");
                } finally {
                    Cerebros.OFFLINE.remove();
                    Config.getInstance().villagerChatAIUseTools = herramientas;
                }
                v.setAiDisabled(false);
            }
            miroDetras |= Direction.fromRotation(v.getHeadYaw()) == Direction.WEST;
            BlockPos salida = Recuerdos.of(world, v).sitio("salida alternativa");
            if (salida == null) {
                return null;
            }
            boolean ordenClara = !MenteLocal.read("¿Hay salida por detrás?").order()
                    && !MenteLocal.read("No subas por detrás.").order();
            boolean ok = ordenClara && respuesta.contains("detrás") && miroDetras && roturas == 0 && salida.getX() < o.getX() && salida.getY() >= o.getY() + 4;
            Pruebas.check(name, ok, "giró y subió por detrás en " + ticks + " ticks; " + state());
            return ok;
        }

        @Override
        String state() {
            return respuesta + " en " + v.getBlockPos().toShortString() + ", atrás " + miroDetras + ", bloques rotos " + roturas
                    + ", " + Retina.memory(v).doing();
        }

        @Override
        void cleanup() {
            Toques.escuchar(v, null);
            OpenAIChatAI.forget(v.getUuid());
            Voluntad.olvidar(v);
            v.discard();
        }
    }

    private static final class Agua extends Pruebas.Scenario {
        private final BlockPos o = new BlockPos(34240, 99, 33000);
        private final VillagerEntityMCA v;
        private final Motor motor = new Motor();
        private final RescateAgua rescate = new RescateAgua();
        private boolean nadaba;

        Agua(ServerWorld world) {
            super("agua-exploracion", world, 460);
            Pruebas.load(world, o, 20);
            Pruebas.fill(world, o.add(-7, -1, -5), o.add(7, 2, 5), Blocks.STONE.getDefaultState());
            Pruebas.fill(world, o.add(-7, 3, -5), o.add(7, 8, 5), Blocks.AIR.getDefaultState());
            Pruebas.fill(world, o.add(-2, 0, -1), o.add(2, 2, 1), Blocks.WATER.getDefaultState());
            v = Pruebas.villager(world, o, Direction.EAST, "Lía Agua");
            v.getBrain().forget(MemoryModuleTypeMCA.STAYING.get());
        }

        @Override
        Boolean step() {
            nadaba |= rescate.tick(world, v, motor);
            BlockPos orilla = Recuerdos.of(world, v).sitio("orilla segura");
            if (orilla == null) {
                return null;
            }
            boolean ok = nadaba && v.isAlive() && !v.isTouchingWater() && v.isOnGround()
                    && orilla.getY() == o.getY() + 3 && world.getBlockState(o).isOf(Blocks.WATER) && v.getInventory().isEmpty();
            Pruebas.check(name, ok, "nadó hasta un borde firme sin gastar bloques en " + ticks + " ticks; " + state());
            return ok;
        }

        @Override
        String state() {
            return v.getBlockPos().toShortString() + ", aire " + v.getAir() + ", activo " + rescate.activo() + ", " + Retina.memory(v).doing();
        }

        @Override
        void cleanup() {
            v.discard();
        }
    }

    private static final class AguaProfunda extends Pruebas.Scenario {
        private final BlockPos o = new BlockPos(34440, 99, 33000);
        private final VillagerEntityMCA v;
        private final ProspectingTask task = new ProspectingTask();
        private int aireMinimo = 300;

        AguaProfunda(ServerWorld world) {
            super("agua-profunda", world, 800);
            Pruebas.load(world, o, 20);
            Pruebas.fill(world, o.add(-7, -1, -5), o.add(7, 11, 5), Blocks.STONE.getDefaultState());
            Pruebas.fill(world, o.add(-7, 12, -5), o.add(7, 18, 5), Blocks.AIR.getDefaultState());
            Pruebas.fill(world, o.add(-2, 0, -1), o.add(2, 11, 1), Blocks.WATER.getDefaultState());
            v = Pruebas.villager(world, o, Direction.EAST, "Eva Agua");
            v.getBrain().forget(MemoryModuleTypeMCA.STAYING.get());
            v.setAiDisabled(true);
        }

        @Override
        Boolean step() {
            if (ticks < 5) return null;
            if (ticks == 5) {
                v.setAir(180);
                v.setAiDisabled(false);
            }
            aireMinimo = Math.min(aireMinimo, v.getAir());
            task.practice(world, v);
            if (!v.isAlive() || v.getHealth() < v.getMaxHealth()) {
                Pruebas.check(name, false, "perdió vida durante el rescate; " + state());
                return false;
            }
            if (ticks % 40 == 0) {
                MCA.LOGGER.info("PRUEBA agua-profunda t={}: {}, sumergido {}, velocidad {}", ticks, state(),
                        v.isSubmergedInWater(), v.getVelocity());
            }
            BlockPos orilla = Recuerdos.of(world, v).sitio("orilla segura");
            if (orilla == null) return null;
            boolean ok = v.isAlive() && v.getHealth() == v.getMaxHealth() && v.isOnGround() && !v.isTouchingWater()
                    && orilla.getY() == o.getY() + 12 && world.getBlockState(o).isOf(Blocks.WATER) && v.getInventory().isEmpty();
            Pruebas.check(name, ok, "el ciclo de exploración salió de 12 bloques de agua en " + ticks + " ticks; " + state());
            return ok;
        }

        @Override
        String state() {
            return v.getBlockPos().toShortString() + ", aire mínimo " + aireMinimo + ", vida " + v.getHealth() + ", " + task.debug();
        }

        @Override
        void cleanup() {
            v.discard();
        }
    }

    private static final class AguaTunel extends Pruebas.Scenario {
        private final BlockPos o = new BlockPos(34340, 99, 33000);
        private final VillagerEntityMCA v;
        private final Motor motor = new Motor();
        private final RescateAgua rescate = new RescateAgua();

        AguaTunel(ServerWorld world) {
            super("agua-respiradero", world, 380);
            Pruebas.load(world, o, 20);
            Pruebas.fill(world, o.add(-5, -1, -4), o.add(9, 3, 4), Blocks.STONE.getDefaultState());
            Pruebas.fill(world, o.add(-5, 4, -4), o.add(9, 8, 4), Blocks.AIR.getDefaultState());
            Pruebas.fill(world, o.add(0, 0, -1), o.add(4, 1, 1), Blocks.WATER.getDefaultState());
            Pruebas.fill(world, o.east(4).up(2), o.east(4).up(3), Blocks.WATER.getDefaultState());
            v = Pruebas.villager(world, o, Direction.EAST, "Ada Agua");
            v.getBrain().forget(MemoryModuleTypeMCA.STAYING.get());
        }

        @Override
        Boolean step() {
            if (ticks < 5) return null;
            if (ticks == 5) v.setAir(95);
            rescate.tick(world, v, motor);
            BlockPos orilla = Recuerdos.of(world, v).sitio("orilla segura");
            if (orilla == null) return null;
            boolean ok = v.isAlive() && v.getHealth() == v.getMaxHealth() && v.isOnGround() && !v.isTouchingWater()
                    && orilla.getY() == o.getY() + 4 && world.getBlockState(o.up(2)).isOf(Blocks.STONE) && v.getInventory().isEmpty();
            Pruebas.check(name, ok, "salió por un respiradero lateral en " + ticks + " ticks; " + state());
            return ok;
        }

        @Override
        String state() {
            return v.getBlockPos().toShortString() + ", aire " + v.getAir() + ", vida " + v.getHealth() + ", activo " + rescate.activo();
        }

        @Override
        void cleanup() {
            v.discard();
        }
    }
}
