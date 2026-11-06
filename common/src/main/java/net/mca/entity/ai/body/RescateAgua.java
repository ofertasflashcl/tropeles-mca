package net.mca.entity.ai.body;

import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.mente.Recuerdos;
import net.mca.entity.ai.sense.Retina;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immediate, local swim to dry footing. A model is never on the critical path to breathe. */
public final class RescateAgua {
    private static final int RADIO = 7;
    private static final int CUOTA = 160;
    private List<BlockPos> camino = List.of();
    private int paso;
    private long replantear;
    private long vence;

    public boolean activo() {
        return !camino.isEmpty();
    }

    public boolean tick(ServerWorld world, VillagerEntityMCA v, Motor motor) {
        long now = world.getTime();
        BlockPos pies = Mining.pies(v);
        if (!v.isTouchingWater() && !v.isSubmergedInWater()) {
            if (!activo() || v.isOnGround()) {
                if (activo() && SondeoSalida.seco(world, pies) && Motor.standCell(world, pies)) {
                    Recuerdos.sitio(v, "orilla segura", pies);
                    Recuerdos.anotar(v, "camino", "salí del agua a terreno firme", pies);
                }
                camino = List.of();
                return false;
            }
        }
        if (!activo() || now >= vence || !libre(world, camino.get(paso))) {
            if (now < replantear) {
                return false;
            }
            replantear = now + 16;
            camino = buscar(world, v, pies);
            if (camino.isEmpty()) {
                return false;
            }
            paso = 0;
            vence = now + 70;
            motor.reset(world, v);
        }
        while (paso + 1 < camino.size() && pies.equals(camino.get(paso))) {
            paso++;
            vence = now + 70;
        }
        BlockPos meta = camino.get(paso);
        Vec3d at = Vec3d.ofBottomCenter(meta);
        v.getBrain().forget(MemoryModuleType.WALK_TARGET);
        v.getNavigation().stop();
        v.getMoveControl().moveTo(at.x, at.y, at.z, 0.85);
        if (meta.getY() > pies.getY() || v.isSubmergedInWater() || SondeoSalida.seco(world, meta)) {
            v.getJumpControl().setActive();
        }
        Vec3d avance = new Vec3d(at.x - v.getX(), 0, at.z - v.getZ());
        if (avance.horizontalLengthSquared() > 0.09) {
            v.addVelocity(avance.normalize().multiply(0.055));
        }
        Retina.memory(v).doing("nadando hacia una orilla segura", now);
        return true;
    }

    private static List<BlockPos> buscar(ServerWorld world, VillagerEntityMCA v, BlockPos inicio) {
        ArrayDeque<BlockPos> cola = new ArrayDeque<>();
        Map<BlockPos, BlockPos> padre = new HashMap<>();
        padre.put(inicio, inicio);
        cola.add(inicio);
        BlockPos respirar = null;
        int n = 0;
        while (!cola.isEmpty() && n++ < CUOTA) {
            BlockPos at = cola.removeFirst();
            if (!at.equals(inicio)) {
                if (SondeoSalida.seco(world, at) && Motor.standCell(world, at)) {
                    return ruta(padre, at, inicio);
                }
                if (respirar == null && v.isSubmergedInWater() && world.getFluidState(at.up()).isEmpty() && at.getY() >= inicio.getY()) {
                    respirar = at;
                }
            }
            for (Direction d : Direction.Type.HORIZONTAL) {
                BlockPos lado = at.offset(d);
                agregar(world, padre, cola, at, lado, inicio);
                if (Mining.passable(world, at.up(2))) {
                    agregar(world, padre, cola, at, lado.up(), inicio);
                }
            }
            if (Agua.water(world, at) || Agua.water(world, at.up())) {
                agregar(world, padre, cola, at, at.up(), inicio);
                agregar(world, padre, cola, at, at.down(), inicio);
            }
        }
        return respirar == null ? List.of() : ruta(padre, respirar, inicio);
    }

    private static void agregar(ServerWorld world, Map<BlockPos, BlockPos> padre, ArrayDeque<BlockPos> cola,
                                BlockPos desde, BlockPos at, BlockPos inicio) {
        if (!padre.containsKey(at) && at.isWithinDistance(inicio, RADIO) && libre(world, at)
                && (Agua.water(world, at) || Agua.water(world, at.up()) || Motor.standCell(world, at))) {
            padre.put(at, desde);
            cola.add(at);
        }
    }

    private static boolean libre(ServerWorld world, BlockPos at) {
        return world.isChunkLoaded(at) && world.isInBuildLimit(at.up()) && world.getWorldBorder().contains(at)
                && Mining.passable(world, at) && Mining.passable(world, at.up()) && !Mining.heat(world, at);
    }

    private static List<BlockPos> ruta(Map<BlockPos, BlockPos> padre, BlockPos at, BlockPos inicio) {
        List<BlockPos> out = new ArrayList<>();
        for (; !at.equals(inicio); at = padre.get(at)) {
            out.add(at);
        }
        Collections.reverse(out);
        return List.copyOf(out);
    }
}
