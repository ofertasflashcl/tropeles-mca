package net.mca.entity.ai.nucleo;

import net.mca.MCA;
import net.mca.util.WorldUtils;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Every villager's {@link Mente}, saved with the world (in the overworld, so a villager keeps its mind across
 * dimensions). A mind is read from its saved form only when that villager is first thought about. Leaving RAM loses
 * nothing: a mind not used for the longest goes back to its saved form, keeping its most important incidents; only a
 * world with more than {@value #MAX} remembered villagers forgets the one untouched the longest.
 */
final class NucleoMundo extends PersistentState {
    /** Minds kept as objects in RAM. */
    private static final int MAX_VIVAS = 256;
    /** Identities kept at all. */
    private static final int MAX = 4096;
    /** Incidents a mind keeps when it leaves RAM. */
    private static final int ARCHIVO = 160;

    final Map<UUID, Mente> vivas = new HashMap<>();
    private final Map<UUID, NbtCompound> crudas = new HashMap<>();

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
        return WorldUtils.loadData(home, NucleoMundo::new, w -> new NucleoMundo(), "mca_tropeles_nucleo");
    }

    Mente of(UUID id, long now) {
        Mente m = vivas.get(id);
        if (m == null) {
            NbtCompound raw = crudas.remove(id);
            try {
                m = raw == null ? new Mente() : Mente.fromNbt(raw);
            } catch (RuntimeException e) {
                MCA.LOGGER.warn("[Tropeles] la mente guardada de {} no se pudo leer: {}", id, e.toString());
                m = new Mente();
            }
            m.mundo = this;
            m.tocado = Math.max(m.tocado, now);
            vivas.put(id, m);
            recortar(id, now);
        }
        return m;
    }

    private void recortar(UUID keep, long now) {
        while (vivas.size() > MAX_VIVAS) {
            UUID oldest = null;
            long when = Long.MAX_VALUE;
            for (Map.Entry<UUID, Mente> e : vivas.entrySet()) {
                if (!e.getKey().equals(keep) && e.getValue().tocado < when) {
                    when = e.getValue().tocado;
                    oldest = e.getKey();
                }
            }
            if (oldest == null) {
                break;
            }
            Mente m = vivas.remove(oldest);
            try {
                m.compactar(now, ARCHIVO);
                crudas.put(oldest, m.toNbt());
            } catch (RuntimeException e) {
                MCA.LOGGER.warn("[Tropeles] la mente de {} no se pudo guardar al salir de memoria: {}", oldest, e.toString());
            }
            markDirty();
        }
        while (vivas.size() + crudas.size() > MAX && !crudas.isEmpty()) {
            UUID oldest = null;
            long when = Long.MAX_VALUE;
            for (Map.Entry<UUID, NbtCompound> e : crudas.entrySet()) {
                if (e.getValue().getLong("tocado") < when) {
                    when = e.getValue().getLong("tocado");
                    oldest = e.getKey();
                }
            }
            crudas.remove(oldest);
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
