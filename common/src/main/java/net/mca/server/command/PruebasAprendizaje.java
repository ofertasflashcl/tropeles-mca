package net.mca.server.command;

import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.MemoryModuleTypeMCA;
import net.mca.entity.ai.body.Motor;
import net.mca.entity.ai.body.RescateAgua;
import net.mca.entity.ai.mente.Aprendizaje;
import net.mca.entity.ai.mente.Recuerdos;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static net.mca.entity.ai.mente.Aprendizaje.Agua.ABIERTA;
import static net.mca.entity.ai.mente.Aprendizaje.Agua.TECHO;
import static net.mca.entity.ai.mente.Aprendizaje.Desenlace.AIRE_BAJO;
import static net.mca.entity.ai.mente.Aprendizaje.Desenlace.ORILLA;
import static net.mca.entity.ai.mente.Aprendizaje.Maniobra.LADO;
import static net.mca.entity.ai.mente.Aprendizaje.Maniobra.SUBIR;

/** Transfer only the learned outcomes to new bodies in different physical arenas. */
final class PruebasAprendizaje {
    private static final BlockPos ORIGEN = new BlockPos(34700, 99, 33000);
    private static final int SEPARACION = 24;
    private static final String TESTIGO = "Aprendizaje testigo";

    static void run(ServerWorld world, String which) {
        if (which.equals("aprendizaje-reinicio")) {
            Pruebas.start(new Reinicio(world));
            return;
        }
        try {
            memoria();
            Pruebas.check("aprendizaje-memoria", true, "ventana acotada, contexto, contradicción, versión y guardado");
        } catch (RuntimeException e) {
            Pruebas.check("aprendizaje-memoria", false, e.getMessage());
        }
        Pruebas.start(new Traslado(world));
    }

    private static void exige(boolean condicion, String mensaje) {
        if (!condicion) throw new IllegalStateException(mensaje);
    }

    private static void memoria() {
        Aprendizaje a = new Aprendizaje();
        a.registrar(ABIERTA, SUBIR, ORILLA);
        exige(a.preferida(ABIERTA) == null, "Una muestra cambió la política");
        for (int i = 0; i < 20; i++) a.registrar(ABIERTA, SUBIR, ORILLA);
        exige(a.preferida(ABIERTA) == SUBIR && a.preferida(TECHO) == null, "Mezcló contextos o no aprendió");
        exige(a.balance(ABIERTA, SUBIR).equals(new Aprendizaje.Balance(8, 8)), "El historial no está acotado");
        NbtCompound datos = a.guardar();
        a.cargar(new NbtCompound());
        exige(a.preferida(ABIERTA) == null, "Un aldeano antiguo heredó una política");
        a.cargar(datos);
        exige(a.preferida(ABIERTA) == SUBIR, "No volvió la política desde el NBT");
        a.registrar(ABIERTA, SUBIR, AIRE_BAJO);
        exige(a.preferida(ABIERTA) == null, "Insiste tras perder la reserva de aire");
        NbtCompound futura = datos.copy();
        futura.putInt("version", 42);
        a.cargar(futura);
        a.registrar(ABIERTA, LADO, ORILLA);
        exige(a.preferida(ABIERTA) == null && a.guardar().equals(futura), "Alteró datos de una versión futura");
    }

    private static final class Traslado extends Pruebas.Scenario {
        private final List<String> recorridos = new ArrayList<>();
        private VillagerEntityMCA v;
        private Motor motor;
        private RescateAgua rescate;
        private BlockPos origen;
        private NbtCompound fuente = new NbtCompound();
        private int etapa;
        private int inicio;
        private int aireMin;
        private boolean empezo;

        Traslado(ServerWorld world) {
            super("aprendizaje-traslado", world, 650);
            preparar();
        }

        private void preparar() {
            origen = ORIGEN.east(SEPARACION * etapa);
            Pruebas.load(world, origen, 9);
            Pruebas.fill(world, origen.add(-4, -1, -2), origen.add(4, 1, 2), Blocks.STONE.getDefaultState());
            Pruebas.fill(world, origen.add(-4, 2, -2), origen.add(4, 5, 2), Blocks.AIR.getDefaultState());
            Pruebas.fill(world, origen.add(-3, 2, -1), origen.add(3, 3, -1), Blocks.STONE.getDefaultState());
            Pruebas.fill(world, origen.add(-3, 2, 1), origen.add(3, 3, 1), Blocks.STONE.getDefaultState());
            Pruebas.fill(world, origen, origen.up(), Blocks.WATER.getDefaultState());
            if (etapa >= 2) {
                Pruebas.fill(world, origen.east(), origen.east().up(), Blocks.WATER.getDefaultState());
            } else {
                Pruebas.fill(world, origen.east().up(2), origen.east().up(3), Blocks.STONE.getDefaultState());
            }
            v = Pruebas.villager(world, origen, Direction.EAST, etapa == 1 ? TESTIGO : "Aprendizaje " + etapa);
            v.getBrain().forget(MemoryModuleTypeMCA.STAYING.get());
            v.setAiDisabled(true);
            Aprendizaje a = v.getAprendizaje();
            if (etapa == 1 || etapa == 2) {
                a.cargar(fuente);
            } else if (etapa == 4 || etapa == 5) {
                // Same evidence count with the context (U) or move (S) changed.
                Aprendizaje registro = new Aprendizaje();
                registro.cargar(fuente);
                int muestras = registro.balance(ABIERTA, SUBIR).intentos();
                for (int i = 0; i < muestras; i++) {
                    a.registrar(etapa == 4 ? TECHO : ABIERTA, etapa == 4 ? SUBIR : LADO, ORILLA);
                }
            }
            exige(v.getInventory().isEmpty(), "El ensayo empezó con objetos");
            motor = new Motor();
            rescate = new RescateAgua();
            empezo = false;
            inicio = ticks;
            aireMin = v.getMaxAir();
        }

        @Override
        Boolean step() {
            if (!empezo) {
                if (!v.isSubmergedInWater()) return null;
                v.setAir(250);
                v.setAiDisabled(false);
                empezo = true;
            }
            aireMin = Math.min(aireMin, v.getAir());
            rescate.tick(world, v, motor);
            exige(v.isAlive() && v.getHealth() == v.getMaxHealth(), "Daño en el ensayo " + state());
            BlockPos firme = Recuerdos.of(world, v).sitio("orilla segura");
            if (firme == null) return null;
            boolean subio = etapa <= 2;
            int muestras = etapa <= 2 ? etapa + 1 : etapa == 5 ? 3 : 1;
            Aprendizaje.Balance balance = v.getAprendizaje().balance(ABIERTA, subio ? SUBIR : LADO);
            exige(balance.equals(new Aprendizaje.Balance(muestras, muestras)), "Faltó el efecto verificado en " + state() + ": " + balance);
            exige((subio ? firme.getX() < origen.getX() : firme.getX() > origen.getX())
                    && v.isOnGround() && !v.isTouchingWater() && v.getInventory().isEmpty(), "Orilla inesperada en " + state());
            recorridos.add(List.of("fuente 1", "fuente 2", "R", "B", "U", "S").get(etapa) + "="
                    + (subio ? "subir" : "lado") + "/" + (ticks - inicio) + "t/aire " + aireMin);
            if (etapa < 2) {
                fuente = v.getAprendizaje().guardar();
                exige(fuente.getKeys().equals(Set.of("version", "ABIERTA_SUBIR")), "Se exportó información adicional");
            }
            if (etapa == 1) {
                v.setAiDisabled(true);
                v.setInvulnerable(true); // stays on this dry ledge for the second process
            } else {
                v.discard();
            }
            if (++etapa == 6) {
                Pruebas.check(name, true, "orillas comprobadas con física y cuerpos nuevos; " + recorridos);
                return true;
            }
            preparar();
            return null;
        }

        @Override
        String state() {
            return "etapa " + etapa + ", " + v.getBlockPos().toShortString() + ", aire " + v.getAir() + ", " + recorridos;
        }

        @Override
        void cleanup() {
            if (!v.getName().getString().equals(TESTIGO) || etapa == 1) v.discard();
        }
    }

    private static final class Reinicio extends Pruebas.Scenario {
        private VillagerEntityMCA encontrado;

        Reinicio(ServerWorld world) {
            super("aprendizaje-reinicio", world, 220);
            Pruebas.load(world, ORIGEN.east(SEPARACION), 9);
        }

        @Override
        Boolean step() {
            BlockPos pozo = ORIGEN.east(SEPARACION);
            List<VillagerEntityMCA> presentes = world.getEntitiesByClass(VillagerEntityMCA.class, new Box(pozo, pozo).expand(7),
                    villager -> villager.getName().getString().equals(TESTIGO));
            if (presentes.isEmpty()) return null;
            encontrado = presentes.get(0);
            Aprendizaje a = encontrado.getAprendizaje();
            boolean ok = a.preferida(ABIERTA) == SUBIR && a.balance(ABIERTA, SUBIR).equals(new Aprendizaje.Balance(2, 2))
                    && encontrado.getInventory().isEmpty();
            Pruebas.check(name, ok, "testigo recargado del guardado de la entidad: " + a.paraModelo(true));
            return ok;
        }

        @Override
        String state() {
            return "esperando al aldeano guardado";
        }

        @Override
        void cleanup() {
            if (encontrado != null) encontrado.discard();
        }
    }
}
