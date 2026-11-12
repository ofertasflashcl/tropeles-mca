package net.mca.entity.ai.body;

import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.mente.Recuerdos;
import net.mca.entity.ai.mente.Voluntad;
import net.mca.entity.ai.sense.Retina;
import net.mca.entity.ai.sense.VisualMemory;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A directional survey that only walks dry, observed or remembered steps. */
public final class SondeoSalida {
    public enum Estado {BUSCANDO, AFUERA, SIN_PASO}

    private static final int RADIO = 18;
    private static final int BUSQUEDA = 384;
    private static final int PLAZO = 360;
    private static final int OJEADAS = 4;
    private static final long EDAD_VISTA = 100;
    private final Direction rumbo;
    private final RegistryKey<World> mundo;
    private final int generacion;
    private final BlockPos origen;
    private final long hasta;
    private final Set<BlockPos> probados = new HashSet<>();
    private List<BlockPos> via = List.of();
    private int miradas;
    private int tramos;
    private long revisarDesde;

    public SondeoSalida(ServerWorld world, VillagerEntityMCA v, Direction rumbo) {
        this.rumbo = rumbo;
        mundo = world.getRegistryKey();
        generacion = Voluntad.generacion(v.getUuid());
        origen = Mining.pies(v).toImmutable();
        hasta = world.getTime() + PLAZO;
    }

    public boolean vigente(ServerWorld world, VillagerEntityMCA v) {
        return mundo.equals(world.getRegistryKey()) && generacion == Voluntad.generacion(v.getUuid());
    }

    public Estado tick(ServerWorld world, VillagerEntityMCA v, Motor motor) {
        BlockPos pies = Mining.pies(v);
        VisualMemory vista = Retina.memory(v);
        long now = world.getTime();
        if (tramos > 0 && v.isOnGround() && afuera(world, pies)) {
            return Estado.AFUERA;
        }
        if (now >= hasta && !motor.midStep(v)) {
            return Estado.SIN_PASO;
        }
        vista.doing("buscando una salida hacia " + rumbo.asString(), now);
        Recuerdos recuerdos = Recuerdos.of(world, v);
        if (!via.isEmpty()) {
            List<BlockPos> andando = motor.walkingPath();
            int siguiente = motor.walkingIndex();
            if (!motor.midStep(v)) {
                List<BlockPos> pendiente = andando != null && siguiente >= 0 && siguiente < andando.size()
                        ? andando.subList(siguiente, andando.size()) : via;
                BlockPos cortado = null;
                if (now >= revisarDesde) {
                    revisarDesde = now + 6;
                    cortado = Motor.bloqueoVisible(world, v, pendiente, true);
                }
                BlockPos proximo = pendiente.get(0);
                if (proximo.equals(pies) && pendiente.size() > 1) {
                    proximo = pendiente.get(1);
                }
                if (cortado != null || !seco(world, proximo)) {
                    recuerdos.cerrar(cortado != null ? cortado : proximo, now + EDAD_VISTA);
                    probados.remove(via.get(via.size() - 1));
                    motor.reset(world, v);
                    via = List.of();
                    miradas = 0;
                    return Estado.BUSCANDO;
                }
            }
            Motor.Result r = motor.walkPath(world, v, via);
            if (r == Motor.Result.ARRIVED || r == Motor.Result.BLOCKED) {
                if (r == Motor.Result.BLOCKED && andando != null && siguiente > 0 && siguiente < andando.size()) {
                    recuerdos.cerrarPaso(andando.get(siguiente - 1), andando.get(siguiente), now + EDAD_VISTA);
                }
                via = List.of();
                miradas = 0;
            }
            return Estado.BUSCANDO;
        }
        if (!v.isOnGround()) {
            return Estado.BUSCANDO;
        }
        if (miradas >= OJEADAS || tramos >= 8) {
            return Estado.SIN_PASO;
        }
        Motor.standStill(v);
        v.getBrain().forget(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET);
        Vec3d direccion = Vec3d.of(rumbo.getVector());
        Vec3d flanco = Vec3d.of(rumbo.rotateYClockwise().getVector());
        int mirada = miradas++;
        double altura = mirada == 0 ? -1.6 : mirada == 1 ? 1.9 : 0.5;
        double lado = mirada < 2 ? 0 : mirada == 2 ? -3 : 3;
        Hands.aim(v, v.getEyePos().add(direccion.multiply(5)).add(flanco.multiply(lado)).add(0, altura, 0));
        Retina.lookAll(world, v);

        List<BlockPos> destinos = vista.blocks(b -> b.face == Direction.UP && now - b.lastSeen <= EDAD_VISTA
                        && b.pos.isWithinDistance(origen, RADIO) && b.state.getFluidState().isEmpty())
                .stream().map(b -> b.pos.up())
                .filter(at -> at.isWithinDistance(origen, RADIO) && hacia(at) > 0 && at.getY() >= pies.getY()
                        && !at.equals(pies) && !probados.contains(at) && !recuerdos.cerrado(at, now) && seco(world, at)
                        && Motor.standCell(world, at))
                .sorted(Comparator.<BlockPos>comparingInt(at -> afuera(world, at) ? 0 : at.getY() > pies.getY() ? 1 : 2)
                        .thenComparingDouble(at -> at.getSquaredDistance(pies)))
                .limit(4).toList();
        for (BlockPos meta : destinos) {
            probados.add(meta);
            var camino = new Motor.WalkSearch(pies, meta, 0).advance(world, BUSQUEDA,
                    at -> at.isWithinDistance(origen, RADIO) && hacia(at) >= 0
                            && (recuerdos.recuerda(at) || vista.saw(at.down(), now - EDAD_VISTA)),
                    (a, b) -> seco(world, b) && b.getY() >= a.getY() - 1
                            && !recuerdos.cerrado(b, now) && !recuerdos.pasoCerrado(a, b, now));
            if (camino.status() == Motor.WalkStatus.FOUND && camino.path().size() >= 2) {
                via = camino.path();
                tramos++;
                break;
            }
        }
        return Estado.BUSCANDO;
    }

    private int hacia(BlockPos at) {
        return (at.getX() - origen.getX()) * rumbo.getOffsetX() + (at.getZ() - origen.getZ()) * rumbo.getOffsetZ();
    }

    private static boolean afuera(ServerWorld world, BlockPos at) {
        return Motor.standCell(world, at) && seco(world, at) && Mining.onSurface(world, at) && Mining.openGround(world, at);
    }

    public static boolean seco(World world, BlockPos at) {
        return world.isChunkLoaded(at) && world.isInBuildLimit(at.up()) && world.getWorldBorder().contains(at)
                && world.getFluidState(at).isEmpty() && world.getFluidState(at.up()).isEmpty();
    }
}
