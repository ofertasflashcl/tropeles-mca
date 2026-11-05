package net.mca.server.command;

import net.mca.MCA;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Temporary test tickets must not become permanent world load at the next server start. Server thread only. */
final class PruebasChunks {
    private static final Map<ServerWorld, Set<ChunkPos>> OWNED = new WeakHashMap<>();

    private PruebasChunks() { }

    static void load(ServerWorld world, int x, int z) {
        if (world.setChunkForced(x, z, true)) {
            OWNED.computeIfAbsent(world, ignored -> new HashSet<>()).add(new ChunkPos(x, z));
        }
        world.getChunk(x, z);
    }

    /** Keep force-loads that existed before the checks; release only the tickets this helper added. */
    static void release() {
        int released = 0;
        for (var entry : OWNED.entrySet()) {
            for (ChunkPos chunk : entry.getValue()) {
                if (entry.getKey().setChunkForced(chunk.x, chunk.z, false)) released++;
            }
        }
        OWNED.clear();
        MCA.LOGGER.info("[Tropeles] pruebas liberan {} chunks forzados por sus escenarios", released);
    }
}
