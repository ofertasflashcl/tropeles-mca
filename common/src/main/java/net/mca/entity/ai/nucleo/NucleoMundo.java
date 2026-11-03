package net.mca.entity.ai.nucleo;

import net.mca.MCA;
import net.mca.server.world.data.ArchivoTropeles;
import net.mca.util.WorldUtils;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Every villager's {@link Mente}, saved with the world (in the overworld, so a villager keeps its mind across
 * dimensions). At most {@value #MAX_AQUI} minds live here, as objects or as their saved form read only when that
 * villager is first thought about; the one untouched the longest goes whole to its own file in the
 * {@link ArchivoTropeles archive} and comes back from there. No mind is ever cut or forgotten, however many villagers
 * the world has: only RAM and this file are bounded.
 */
final class NucleoMundo extends PersistentState {
    private static final String ARCHIVO = "nucleo";
    /** Minds kept here (as objects or saved form); the rest wait in the archive. */
    private static final int MAX_AQUI = 256;

    final Map<UUID, Mente> vivas = new HashMap<>();
    private final Map<UUID, NbtCompound> crudas = new HashMap<>();
    @Nullable
    private MinecraftServer server;

    private NucleoMundo() {
    }

    private NucleoMundo(NbtCompound nbt) {
        NbtCompound all = nbt.getCompound("mentes");
        for (String key : all.getKeys()) {
            try {
                crudas.put(UUID.fromString(key), all.getCompound(key));
            } catch (IllegalArgumentException ignored) {
                // not a villager id
            }
        }
    }

    static NucleoMundo get(ServerWorld world) {
        ServerWorld home = world.getServer() == null ? world : world.getServer().getOverworld();
        NucleoMundo s = WorldUtils.loadData(home, NucleoMundo::new, w -> new NucleoMundo(), "mca_tropeles_nucleo");
        s.server = world.getServer();
        return s;
    }

    Mente of(UUID id, long now) {
        Mente m = vivas.get(id);
        if (m == null) {
            // here or archived, whichever changed last (after a crash the archive may be newer than this file)
            NbtCompound raw = ArchivoTropeles.masNuevo(crudas.remove(id), ArchivoTropeles.leer(server, ARCHIVO, id));
            try {
                m = raw == null ? new Mente() : Mente.fromNbt(raw);
            } catch (RuntimeException e) {
                MCA.LOGGER.warn("[Tropeles] la mente guardada de {} no se pudo leer: {}", id, e.toString());
                m = new Mente();
            }
            m.mundo = this;
            m.tocado = Math.max(m.tocado, now);
            vivas.put(id, m);
            recortar(id);
        }
        return m;
    }

    /** Over {@value #MAX_AQUI}: the mind untouched the longest goes whole to the archive (kept here if that fails). */
    private void recortar(UUID keep) {
        while (vivas.size() + crudas.size() > MAX_AQUI) {
            UUID oldest = null;
            long when = Long.MAX_VALUE;
            boolean cruda = false;
            for (Map.Entry<UUID, NbtCompound> e : crudas.entrySet()) {
                if (e.getValue().getLong("tocado") < when) {
                    when = e.getValue().getLong("tocado");
                    oldest = e.getKey();
                    cruda = true;
                }
            }
            for (Map.Entry<UUID, Mente> e : vivas.entrySet()) {
                if (!e.getKey().equals(keep) && e.getValue().tocado < when) {
                    when = e.getValue().tocado;
                    oldest = e.getKey();
                    cruda = false;
                }
            }
            if (oldest == null) {
                break;
            }
            NbtCompound raw;
            try {
                raw = cruda ? crudas.get(oldest) : vivas.get(oldest).toNbt();
            } catch (RuntimeException e) {
                MCA.LOGGER.warn("[Tropeles] la mente de {} no se pudo escribir: {}", oldest, e.toString());
                break;
            }
            if (!ArchivoTropeles.guardar(server, ARCHIVO, oldest, raw)) {
                break; // the disk refused: it stays here rather than be lost, and the bound waits
            }
            crudas.remove(oldest);
            vivas.remove(oldest);
            markDirty();
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound all = new NbtCompound();
        crudas.forEach((id, raw) -> all.put(id.toString(), raw));
        vivas.forEach((id, m) -> all.put(id.toString(), m.toNbt()));
        nbt.put("mentes", all);
        return nbt;
    }
}
