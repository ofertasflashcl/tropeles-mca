package net.mca.entity.ai.obra;

import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.sense.Obras;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Where a building goes, chosen the way a player looks for a spot: ground that is near enough level (a little to
 * dig, a little to fill), dry, its own (no field, path, village house or anyone's build in the way; trees and
 * bushes may go), in loaded land, clear of other buildings. Told "aquí", it is the spot the player is looking at,
 * or the nearest good one to it; otherwise the best spot a short walk from the player. The door faces the player.
 */
public final class Sitio {
    /** The site: the plan's origin (its corner, at floor level) and the way its front faces. */
    public record Elegido(BlockPos origen, Direction frente, int tierra, int arboles) {
    }

    private record Candidato(int cx, int cz, Direction frente, int y0, double puntos) {
    }

    /** At most this much ground above the floor is dug away, and this much below it is left hollow under the floor. */
    private static final int CAVAR = 3;
    private static final int RELLENO = 2;

    private Sitio() {
    }

    public static BlockPos origen(BlockPos suelo, Plano plano, Direction frente) {
        return suelo.subtract(plano.centro.rotate(Plano.giro(frente)));
    }

    /** Validates the marked position at the chosen floor height and orientation. */
    @Nullable
    public static Elegido exacto(ServerWorld world, VillagerEntityMCA villager, Plano plano, BlockPos origen,
                                 Direction frente, String[] porQue) {
        ObrasMundo obras = ObrasMundo.get(world);
        int[] coste = obras.tienePlanosIncompatibles() ? null : completo(world, villager, plano, origen, Plano.giro(frente), obras, null);
        if (coste == null) {
            porQue[0] = "la parcela tiene obstáculos, agua, terreno irregular o zonas sin cargar";
            porQue[1] = "the plot has obstacles, water, uneven ground or unloaded areas";
            return null;
        }
        return new Elegido(origen, frente, coste[0], coste[1]);
    }

    /** The block the player is looking at within 48 blocks, or the one under their feet: "aquí". */
    public static BlockPos mirada(PlayerEntity player) {
        HitResult hit = player.raycast(48.0, 1.0F, false);
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult b) {
            return b.getBlockPos().toImmutable();
        }
        return player.getBlockPos().down().toImmutable();
    }

    /**
     * The best site for {@code plano} around {@code ancla} (between {@code radioMin} and {@code radioMax} blocks
     * away), its front toward {@code mirarA}; null when there is none, with why not in {@code porQue[0]} (Spanish)
     * and {@code porQue[1]} (English).
     */
    @Nullable
    public static Elegido buscar(ServerWorld world, VillagerEntityMCA villager, Plano plano, BlockPos ancla, Vec3d mirarA, int radioMin, int radioMax,
                                 @Nullable String salvo, String[] porQue) {
        ObrasMundo obras = ObrasMundo.get(world);
        if (obras.tienePlanosIncompatibles()) {
            porQue[0] = "hay una obra guardada con un plano incompatible; conserva el archivo y revisa el registro del servidor";
            porQue[1] = "a saved project has an incompatible blueprint; preserve the file and check the server log";
            return null;
        }
        List<Candidato> rough = new ArrayList<>();
        int water = 0;
        int steep = 0;
        for (int r = radioMin; r <= radioMax; r += 2) {
            int n = r == 0 ? 1 : Math.max(6, (int) Math.round(2 * Math.PI * r / 3.0));
            for (int i = 0; i < n; i++) {
                double a = 2 * Math.PI * i / n;
                int cx = ancla.getX() + (int) Math.round(Math.cos(a) * r);
                int cz = ancla.getZ() + (int) Math.round(Math.sin(a) * r);
                Direction frente = hacia(cx, cz, mirarA);
                int[] ys = muestras(world, plano, cx, cz, frente);
                if (ys == null) {
                    water++;
                    continue;
                }
                int[] sorted = ys.clone();
                Arrays.sort(sorted);
                int y0 = sorted[sorted.length / 2];
                int earth = 0;
                for (int y : ys) {
                    earth += Math.abs(y - y0);
                }
                if (sorted[sorted.length - 1] - sorted[0] > CAVAR + RELLENO) {
                    steep++;
                    continue;
                }
                rough.add(new Candidato(cx, cz, frente, y0, earth * 1.5 + r * 0.25));
            }
        }
        rough.sort(Comparator.comparingDouble(Candidato::puntos));
        int built = 0;
        Elegido best = null;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < Math.min(14, rough.size()); i++) {
            Candidato c = rough.get(i);
            BlockRotation giro = Plano.giro(c.frente());
            BlockPos origen = origen(new BlockPos(c.cx(), c.y0(), c.cz()), plano, c.frente());
            int[] result = completo(world, villager, plano, origen, giro, obras, salvo);
            if (result == null) {
                built++;
                continue;
            }
            double score = result[0] * 1.0 + result[1] * 0.4 + Math.sqrt(new BlockPos(c.cx(), ancla.getY(), c.cz()).getSquaredDistance(ancla)) * 0.3;
            if (score < bestScore) {
                bestScore = score;
                best = new Elegido(origen, c.frente(), result[0], result[1]);
            }
        }
        if (best == null) {
            if (built > 0) {
                porQue[0] = "ahí hay construcciones, cultivos o caminos que no son míos";
                porQue[1] = "there are buildings, crops or paths there that aren't mine";
            } else if (steep >= water) {
                porQue[0] = "el terreno es muy disparejo para una casa";
                porQue[1] = "the ground is too uneven for a house";
            } else {
                porQue[0] = "está todo lleno de agua";
                porQue[1] = "it's all water";
            }
        }
        return best;
    }

    /** The way from the site's centre toward the player, as one of the four. */
    public static Direction hacia(int cx, int cz, Vec3d to) {
        double dx = to.x - (cx + 0.5);
        double dz = to.z - (cz + 0.5);
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** The ground height at nine points of the footprint, or null when one of them is water, lava or not loaded. */
    @Nullable
    private static int[] muestras(ServerWorld world, Plano plano, int cx, int cz, Direction frente) {
        BlockRotation giro = Plano.giro(frente);
        BlockPos origen = new BlockPos(cx, 0, cz).subtract(plano.centro.rotate(giro));
        int mx = plano.centro.getX();
        int mz = plano.centro.getZ();
        int[][] local = {{plano.minX, plano.minZ}, {plano.maxX, plano.minZ}, {plano.minX, plano.maxZ}, {plano.maxX, plano.maxZ}, {mx, mz}, {mx, plano.minZ},
                {plano.minX, mz}, {plano.maxX, mz}, {mx, plano.maxZ}};
        int[] ys = new int[local.length];
        for (int i = 0; i < local.length; i++) {
            BlockPos at = Plano.enMundo(origen, giro, new BlockPos(local[i][0], 0, local[i][1]));
            int y = suelo(world, at.getX(), at.getZ());
            if (y == Integer.MIN_VALUE) {
                return null;
            }
            ys[i] = y;
        }
        return ys;
    }

    /**
     * The whole check of one site: every column of the footprint near the floor, every cell of the building free
     * of anything made, planted or wet. {earth to move, logs to cut}, or null when it will not do.
     */
    @Nullable
    private static int[] completo(ServerWorld world, VillagerEntityMCA villager, Plano plano, BlockPos origen, BlockRotation giro, ObrasMundo obras,
                                  @Nullable String salvo) {
        BlockPos min = null;
        BlockPos max = null;
        int earth = 0;
        int logs = 0;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int x = plano.minX; x <= plano.maxX; x++) {
            for (int z = plano.minZ; z <= plano.maxZ; z++) {
                BlockPos at = Plano.enMundo(origen, giro, new BlockPos(x, 0, z));
                if (!world.isChunkLoaded(at) || !world.getWorldBorder().contains(at)) {
                    return null;
                }
                int y = suelo(world, at.getX(), at.getZ());
                if (y == Integer.MIN_VALUE || y > origen.getY() + CAVAR || y < origen.getY() - RELLENO) {
                    return null;
                }
                earth += Math.abs(y - origen.getY());
                BlockState ground = world.getBlockState(m.set(at.getX(), y, at.getZ()));
                if (ground.isOf(Blocks.DIRT_PATH) || ground.isOf(Blocks.FARMLAND) || !Obras.natural(ground)) {
                    return null; // a village road, a field, somebody's floor
                }
            }
        }
        for (Plano.Pieza p : plano.piezas) {
            BlockPos at = Plano.enMundo(origen, giro, p.rel());
            if (world.isOutOfHeightLimit(at)) {
                return null;
            }
            min = min == null ? at : new BlockPos(Math.min(min.getX(), at.getX()), Math.min(min.getY(), at.getY()), Math.min(min.getZ(), at.getZ()));
            max = max == null ? at : new BlockPos(Math.max(max.getX(), at.getX()), Math.max(max.getY(), at.getY()), Math.max(max.getZ(), at.getZ()));
            BlockState s = world.getBlockState(at);
            if (s.isAir()) {
                continue;
            }
            if (!s.getFluidState().isEmpty()) {
                return null;
            }
            if (s.isIn(BlockTags.LOGS)) {
                logs++;
            }
            if (s.isIn(BlockTags.LEAVES) || s.isReplaceable()) {
                continue;
            }
            Obras.Kind kind = Obras.kind(world, villager, at);
            if (kind != Obras.Kind.NATURE && kind != Obras.Kind.TREE && kind != Obras.Kind.OWN) {
                return null;
            }
        }
        if (min == null || logs > 40 || obras.choca(min, max, 2, salvo)) {
            return null;
        }
        if (Obras.inVillageBuilding(world, Plano.enMundo(origen, giro, plano.centro.up()))) {
            return null;
        }
        return new int[]{earth, logs};
    }

    /**
     * The height of the ground at a column: the top block that is soil or rock, under any trunk, leaves, plants or
     * snow. Integer.MIN_VALUE for water or lava on top, or nothing loaded there.
     */
    public static int suelo(ServerWorld world, int x, int z) {
        BlockPos.Mutable m = new BlockPos.Mutable(x, world.getBottomY(), z);
        if (!world.isChunkLoaded(m)) {
            return Integer.MIN_VALUE;
        }
        int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        for (int y = top; y > top - 32 && y > world.getBottomY(); y--) {
            m.set(x, y, z);
            BlockState s = world.getBlockState(m);
            if (!s.getFluidState().isEmpty()) {
                return Integer.MIN_VALUE;
            }
            if (s.isAir() || s.isIn(BlockTags.LOGS) || s.isIn(BlockTags.LEAVES) || s.isReplaceable() || s.getCollisionShape(world, m).isEmpty()) {
                continue;
            }
            return y;
        }
        return Integer.MIN_VALUE;
    }
}
