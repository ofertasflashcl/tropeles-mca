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
    private final Direction rumbo;
    private final RegistryKey<World> mundo;
    private final int generacion;
    private final BlockPos origen;
    private final long hasta;
    private final Set<BlockPos> probados = new HashSet<>();
    private List<BlockPos> via = List.of();
    private int miradas;
    private int tramos;

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
        if (!via.isEmpty()) {
            if (!motor.midStep(v) && via.stream().anyMatch(at -> !seco(world, at))) {
                motor.reset(world, v);
                via = List.of();
                return Estado.BUSCANDO;
            }
            Motor.Result r = motor.walkPath(world, v, via);
            if (r == Motor.Result.ARRIVED || r == Motor.Result.BLOCKED) {
                via = List.of();
                miradas = 0;
            }
            return Estado.BUSCANDO;
        }
        if (!v.isOnGround()) {
            return Estado.BUSCANDO;
        }
        if (miradas >= 2 || tramos >= 8) {
            return Estado.SIN_PASO;
        }
        Motor.standStill(v);
        v.getBrain().forget(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET);
        Vec3d direccion = Vec3d.of(rumbo.getVector());
        Hands.aim(v, v.getEyePos().add(direccion.multiply(5)).add(0, miradas++ == 0 ? -1.6 : 1.9, 0));
        Retina.lookAll(world, v);

        Recuerdos recuerdos = Recuerdos.of(world, v);
        List<BlockPos> destinos = vista.blocks(b -> b.face == Direction.UP && now - b.lastSeen <= 100
                        && b.pos.isWithinDistance(origen, RADIO) && b.state.getFluidState().isEmpty())
                .stream().map(b -> b.pos.up())
                .filter(at -> at.isWithinDistance(origen, RADIO) && hacia(at) > 0 && at.getY() >= pies.getY()
                        && !at.equals(pies) && !probados.contains(at) && seco(world, at) && Motor.standCell(world, at))
                .sorted(Comparator.<BlockPos>comparingInt(at -> afuera(world, at) ? 0 : at.getY() > pies.getY() ? 1 : 2)
                        .thenComparingDouble(at -> at.getSquaredDistance(pies)))
                .limit(4).toList();
        for (BlockPos meta : destinos) {
            probados.add(meta);
            var camino = new Motor.WalkSearch(pies, meta, 0).advance(world, BUSQUEDA,
                    at -> at.isWithinDistance(origen, RADIO) && hacia(at) >= 0
                            && (recuerdos.recuerda(at) || vista.stateAt(at.down()) != null),
                    (a, b) -> seco(world, b) && b.getY() >= a.getY() - 1 && !recuerdos.pasoCerrado(a, b, now));
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
