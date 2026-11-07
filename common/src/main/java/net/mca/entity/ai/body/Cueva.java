package net.mca.entity.ai.body;

import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.mente.Recuerdos;
import net.mca.entity.ai.sense.Retina;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** An excursion from the work face, using only reversible dry steps and returning by the same cells. */
public final class Cueva {
    private static final int PASOS = 32;
    private static final int DISTANCIA = 20;
    private static final int DURACION = 1000;
    private static final int MEMORIA = 24000;
    private final Deque<BlockPos> camino = new ArrayDeque<>();
    private final Set<BlockPos> vistos = new HashSet<>();
    private final Map<BlockPos, Long> revisados = new LinkedHashMap<>();
    private List<BlockPos> paso = List.of();
    private long hasta;
    private long observarHasta;
    private boolean regreso;
    private boolean retrocede;

    public boolean activa() {
        return !camino.isEmpty();
    }

    public boolean caminando() {
        return !paso.isEmpty();
    }

    public boolean buscandoMinerales() {
        return activa() && !caminando() && !regreso;
    }

    public boolean abrir(ServerWorld world, VillagerEntityMCA v, BlockPos pies, @Nullable BlockPos atras) {
        long now = world.getTime();
        if (!seguro(world, pies) || revisados.getOrDefault(pies, 0L) > now) {
            return false;
        }
        for (Direction d : Direction.Type.HORIZONTAL) {
            BlockPos puerta = vecino(world, pies, d);
            if (puerta == null || puerta.equals(atras) || revisados.getOrDefault(puerta, 0L) > now || !visible(world, v, puerta)) {
                continue;
            }
            if (!abertura(world, v, puerta, d, !libre(world, pies.up(2)))) {
                continue;
            }
            camino.add(pies);
            vistos.add(pies);
            vistos.add(puerta);
            if (atras != null) {
                vistos.add(atras);
            }
            paso = List.of(pies, puerta);
            hasta = now + DURACION;
            Recuerdos.sitio(v, "cueva", puerta);
            Recuerdos.anotar(v, "exploracion", "encontré una cueva mientras minaba y marqué por dónde volver", pies);
            return true;
        }
        return false;
    }

    /** Returns true for the entire excursion, including the last tick back at the tunnel. */
    public boolean tick(ServerWorld world, VillagerEntityMCA v, Motor motor) {
        long now = world.getTime();
        if (!activa()) {
            return false;
        }
        Retina.memory(v).doing(regreso ? "volviendo de la cueva al túnel" : "explorando la cueva", now);
        if (caminando()) {
            BlockPos destino = paso.get(1);
            if (!motor.midStep(v) && !seguro(world, destino)) {
                motor.reset(world, v);
                paso = List.of();
                if (retrocede || !camino.peekLast().equals(Mining.pies(v))) {
                    cerrar(now);
                }
                return true;
            }
            Motor.Result r = motor.walkPath(world, v, paso);
            if (r == Motor.Result.RUNNING) {
                return true;
            }
            paso = List.of();
            if (r == Motor.Result.BLOCKED) {
                if (retrocede || !camino.peekLast().equals(Mining.pies(v))) {
                    cerrar(now);
                }
                return true;
            }
            if (retrocede) {
                camino.removeLast();
            } else {
                camino.addLast(destino);
                observarHasta = now + 10;
            }
            return true;
        }
        BlockPos pies = Mining.pies(v);
        if (!pies.equals(camino.peekLast()) || !seguro(world, pies)) {
            cerrar(now);
            return true;
        }
        if (vistos.size() >= PASOS || now >= hasta) {
            regreso = true;
        }
        if (!regreso && now < observarHasta) {
            Motor.standStill(v);
            return true;
        }
        if (!regreso) {
            for (int giro = 0; giro < 4; giro++) {
                Direction d = Direction.fromHorizontal((v.getHorizontalFacing().getHorizontal() + giro) & 3);
                BlockPos vecino = vecino(world, pies, d);
                if (vecino == null || revisados.getOrDefault(vecino, 0L) > now
                        || !vecino.isWithinDistance(camino.peekFirst(), DISTANCIA) || !visible(world, v, vecino) || !vistos.add(vecino)) {
                    continue;
                }
                retrocede = false;
                paso = List.of(pies, vecino);
                return true;
            }
        }
        if (camino.size() == 1) {
            Recuerdos.anotar(v, "exploracion", "volví por mis pasos de la cueva y seguí minando desde el túnel", pies);
            cerrar(now);
            return true;
        }
        BlockPos anterior = camino.stream().skip(camino.size() - 2L).findFirst().orElseThrow();
        retrocede = true;
        paso = List.of(pies, anterior);
        return true;
    }

    public void cerrar(long now) {
        for (BlockPos pos : vistos) {
            revisados.put(pos, now + MEMORIA);
        }
        while (revisados.size() > 256) {
            revisados.remove(revisados.keySet().iterator().next());
        }
        camino.clear();
        vistos.clear();
        paso = List.of();
        regreso = false;
        retrocede = false;
        observarHasta = 0;
    }

    private static boolean abertura(ServerWorld world, VillagerEntityMCA v, BlockPos puerta, Direction d, boolean tunelBajo) {
        // A short ray into natural cave air, a high ceiling, or a wider pocket. A two-high work tunnel is not a cue.
        for (int i = 0; i < 3; i++) {
            BlockPos at = puerta.offset(d, i);
            if (!seguro(world, at) || !visible(world, v, at)) {
                break;
            }
            if (world.getBlockState(at).isOf(Blocks.CAVE_AIR)) {
                return true;
            }
            if (tunelBajo && (libre(world, at.up(2)) && visible(world, v, at.up())
                    || seguro(world, at.offset(d.rotateYClockwise())) && visible(world, v, at.offset(d.rotateYClockwise()))
                    || seguro(world, at.offset(d.rotateYCounterclockwise())) && visible(world, v, at.offset(d.rotateYCounterclockwise())))) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static BlockPos vecino(ServerWorld world, BlockPos from, Direction d) {
        BlockPos at = from.offset(d);
        if (seguro(world, at)) {
            return at;
        }
        if (seguro(world, at.up()) && libre(world, from.up(2))) {
            return at.up();
        }
        if (seguro(world, at.down()) && libre(world, at.up())) {
            return at.down();
        }
        return null;
    }

    private static boolean seguro(ServerWorld world, BlockPos at) {
        return world.isChunkLoaded(at) && world.getWorldBorder().contains(at) && libre(world, at)
                && libre(world, at.up()) && Motor.standCell(world, at);
    }

    private static boolean libre(ServerWorld world, BlockPos at) {
        return world.getFluidState(at).isEmpty() && Mining.passable(world, at);
    }

    private static boolean visible(ServerWorld world, VillagerEntityMCA v, BlockPos at) {
        Vec3d punto = Vec3d.ofBottomCenter(at).add(0, 1.0, 0);
        return world.raycast(new RaycastContext(v.getEyePos(), punto, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.ANY, v)).getType() == HitResult.Type.MISS;
    }
}
