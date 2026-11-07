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
    private static final int CUOTA = 256;
    private static final int AIRE_URGENTE = 100;
    private List<BlockPos> camino = List.of();
    private int paso;
    private long replantear;
    private long vence;
    private boolean urgente;
    private final Map<BlockPos, Long> bloqueados = new HashMap<>();

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
        if (activo() && paso == camino.size() - 1 && pies.equals(camino.get(paso)) && !SondeoSalida.seco(world, pies)) {
            camino = List.of(); // a swim step or air pocket is progress, not the end of the rescue
            replantear = now;
        }
        boolean sinAire = v.isSubmergedInWater() && v.getAir() <= AIRE_URGENTE;
        boolean cambioPrioridad = sinAire && !urgente;
        urgente = sinAire;
        boolean fallo = activo() && (now >= vence || !libre(world, camino.get(paso)));
        if (!activo() || fallo || cambioPrioridad) {
            if (now < replantear) {
                return false;
            }
            if (fallo) {
                bloqueados.put(camino.get(paso), now + 100);
            }
            bloqueados.values().removeIf(hasta -> hasta <= now);
            replantear = now + 16;
            camino = buscar(world, v, pies, bloqueados);
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
        if (meta.getY() < pies.getY() && v.isTouchingWater()) {
            v.addVelocity(0, -0.06, 0);
        } else if (meta.getY() > pies.getY() || SondeoSalida.seco(world, meta)
                || v.isSubmergedInWater() && !Agua.water(world, meta.up())) {
            v.getJumpControl().setActive();
        }
        Vec3d avance = new Vec3d(at.x - v.getX(), 0, at.z - v.getZ());
        if (avance.horizontalLengthSquared() > 0.09) {
            v.addVelocity(avance.normalize().multiply(0.055));
        }
        Retina.memory(v).doing("nadando hacia una orilla segura", now);
        return true;
    }

    private static List<BlockPos> buscar(ServerWorld world, VillagerEntityMCA v, BlockPos inicio, Map<BlockPos, Long> bloqueados) {
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
                    if (v.getAir() <= AIRE_URGENTE) return ruta(padre, respirar, inicio);
                }
            }
            for (Direction d : Direction.Type.HORIZONTAL) {
                BlockPos lado = at.offset(d);
                agregar(world, padre, cola, at, lado, inicio, bloqueados);
                if (Mining.passable(world, at.up(2))) {
                    agregar(world, padre, cola, at, lado.up(), inicio, bloqueados);
                }
            }
            if (Agua.water(world, at) || Agua.water(world, at.up())) {
                agregar(world, padre, cola, at, at.up(), inicio, bloqueados);
                agregar(world, padre, cola, at, at.down(), inicio, bloqueados);
            }
        }
        if (respirar != null) return ruta(padre, respirar, inicio);
        // In deep water neither air nor a bank fits in this local search. Keep swimming up through a verified
        // free cell instead of abandoning the rescue and resuming the exploration that put it under water.
        BlockPos subir = inicio.up();
        return v.isSubmergedInWater() && Agua.water(world, inicio) && !bloqueados.containsKey(subir) && libre(world, subir)
                ? List.of(subir) : List.of();
    }

    private static void agregar(ServerWorld world, Map<BlockPos, BlockPos> padre, ArrayDeque<BlockPos> cola,
                                BlockPos desde, BlockPos at, BlockPos inicio, Map<BlockPos, Long> bloqueados) {
        if (!padre.containsKey(at) && !bloqueados.containsKey(at) && at.isWithinDistance(inicio, RADIO) && libre(world, at)
                && (Agua.water(world, at) || Agua.water(world, at.up()) || Motor.standCell(world, at))) {
            padre.put(at, desde);
            cola.add(at);
        }
    }

    private static boolean libre(ServerWorld world, BlockPos at) {
        if (!world.isChunkLoaded(at) || !world.isInBuildLimit(at) || !world.isInBuildLimit(at.up()) || !world.getWorldBorder().contains(at)
                || !Mining.passable(world, at) || !Mining.passable(world, at.up())) return false;
        for (Direction d : Direction.values()) {
            BlockPos cerca = at.offset(d);
            if (world.isChunkLoaded(cerca) && world.getFluidState(cerca).isIn(net.minecraft.registry.tag.FluidTags.LAVA)) return false;
        }
        return true;
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
