package net.mca.server.world.data;

import net.mca.MCA;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The diary's cold tier: fixed-size NBT pages, one villager/dimension, no configured lifetime entry limit.
 * Before a page changes, its complete pending record is saved in the index. Opening the archive replays it
 * idempotently, so a crash between page and index updates cannot silently lose an evicted hot entry.
 * Uses ArchivoTropeles's checked revisions and atomic replacement, not a power-loss/fsync guarantee.
 */
public final class CronicaTropeles {
    public static final int PAGE_SIZE = 256;
    private static final int READ_PAGES = 4;
    public record Entrada(long orden, NbtCompound dato) { }

    private final MinecraftServer server;
    private final UUID owner;
    private final String kind;
    private NbtCompound index;
    private long cachedNumber = -1;
    private NbtCompound cached;

    public CronicaTropeles(MinecraftServer server, String dimension, UUID owner) {
        this.server = server;
        this.owner = owner;
        // Encode, do not replace punctuation: distinct dimension ids must never alias the same folder.
        this.kind = "cronica/v1/" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(dimension.getBytes(StandardCharsets.UTF_8));
        NbtCompound saved = read(kind + "/indice");
        index = saved == null ? fresh() : saved;
        if (saved == null) index.putLong("siguiente", 0);
        if (!index.contains("siguiente", NbtElement.LONG_TYPE) || index.getLong("siguiente") < 0) throw new IllegalStateException("Indice de cronica invalido");
        recover();
    }

    private static NbtCompound fresh() {
        NbtCompound n = new NbtCompound();
        n.putInt("v", 1);
        n.putLong("revision", 1);
        return n;
    }

    private NbtCompound read(String folder) {
        NbtCompound n = ArchivoTropeles.leer(server, folder, owner);
        if (n == null && ArchivoTropeles.existe(server, folder, owner)) {
            throw new IllegalStateException("Cronica ilegible: " + folder + "/" + owner);
        }
        if (n != null && n.getInt("v") != 1) throw new IllegalStateException("Version de cronica desconocida");
        return n;
    }

    private String pageKind(long number) { return kind + "/paginas/" + number; }

    private NbtCompound page(long number) {
        if (number != cachedNumber) {
            NbtCompound p = read(pageKind(number));
            if (p == null) {
                p = fresh();
                p.putLong("pagina", number);
                p.put("datos", new NbtCompound());
            }
            if (p.getLong("pagina") != number || !p.contains("datos", NbtElement.COMPOUND_TYPE) || p.getCompound("datos").getKeys().size() > PAGE_SIZE) {
                throw new IllegalStateException("Pagina de cronica invalida: " + number);
            }
            for (String key : p.getCompound("datos").getKeys()) {
                long id = Long.parseLong(key);
                if (id < 0 || id / PAGE_SIZE != number || !p.getCompound("datos").contains(key, NbtElement.COMPOUND_TYPE)) throw new IllegalStateException("Entrada fuera de su pagina");
            }
            cachedNumber = number;
            cached = p;
        }
        return cached;
    }

    private static NbtCompound revised(NbtCompound n) {
        NbtCompound copy = n.copy();
        copy.putLong("revision", Math.incrementExact(n.getLong("revision")));
        return copy;
    }

    private void saveIndex(NbtCompound next) {
        if (!ArchivoTropeles.guardar(server, kind + "/indice", owner, next)) {
            throw new IllegalStateException("No se pudo guardar el indice de cronica");
        }
        index = next;
    }

    private void writePages(List<Entrada> batch) {
        Map<Long, List<Entrada>> groups = new TreeMap<>();
        for (Entrada e : batch) groups.computeIfAbsent(e.orden() / PAGE_SIZE, k -> new ArrayList<>()).add(e);
        for (Map.Entry<Long, List<Entrada>> group : groups.entrySet()) {
            long number = group.getKey();
            NbtCompound old = page(number);
            NbtCompound next = revised(old);
            NbtCompound entries = next.getCompound("datos");
            boolean changed = false;
            for (Entrada e : group.getValue()) {
                String key = Long.toString(e.orden());
                if (entries.contains(key)) {
                    if (!entries.getCompound(key).equals(e.dato())) throw new IllegalStateException("Identidad de recuerdo en conflicto: " + e.orden());
                } else {
                    entries.put(key, e.dato().copy());
                    changed = true;
                }
            }
            if (changed && !ArchivoTropeles.guardar(server, pageKind(number), owner, next)) {
                throw new IllegalStateException("No se pudo guardar la pagina de cronica " + number);
            }
            if (changed) cached = next;
        }
    }

    private void recover() {
        if (!index.contains("pendiente")) return;
        NbtCompound pending = index.getCompound("pendiente");
        NbtList records = pending.getList("registros", NbtElement.COMPOUND_TYPE);
        List<Entrada> batch = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            NbtCompound e = records.getCompound(i);
            if (!e.contains("dato")) throw new IllegalStateException("Transaccion de cronica invalida");
            batch.add(new Entrada(e.getLong("orden"), e.getCompound("dato")));
        }
        validate(batch);
        writePages(batch);
        NbtCompound next = revised(index);
        next.putLong("siguiente", Math.max(index.getLong("siguiente"), Math.incrementExact(batch.get(batch.size() - 1).orden())));
        next.remove("pendiente");
        saveIndex(next);
    }

    /** A sequence watermark, independent of world/day time. It never reuses an archived identity after reload. */
    public long siguiente() { return index.getLong("siguiente"); }

    private static void validate(List<Entrada> batch) {
        if (batch.isEmpty() || batch.size() > PAGE_SIZE) throw new IllegalArgumentException("Lote de cronica invalido");
        long previous = -1;
        for (Entrada e : batch) {
            if (e.orden() < 0 || e.orden() == Long.MAX_VALUE || previous >= 0 && e.orden() != previous + 1) {
                throw new IllegalArgumentException("Orden de recuerdo invalido");
            }
            previous = e.orden();
        }
    }

    /** Only true after both the page and its index committed. A failure leaves the hot entry with the caller. */
    public boolean guardar(long id, NbtCompound data) {
        return guardar(List.of(new Entrada(id, data)));
    }

    /** Amortize compression and atomic replacement: up to 256 consecutive records touch at most two pages. */
    public boolean guardar(List<Entrada> batch) {
        try {
            validate(batch);
            recover();
            cachedNumber = -1; // recheck disk before discarding a caller's only hot copy, including replayed duplicates
            cached = null;
            boolean missing = false;
            for (Entrada e : batch) {
                NbtCompound entries = page(e.orden() / PAGE_SIZE).getCompound("datos");
                String key = Long.toString(e.orden());
                if (!entries.contains(key)) missing = true;
                else if (!entries.getCompound(key).equals(e.dato())) throw new IllegalStateException("Identidad de recuerdo en conflicto: " + e.orden());
            }
            if (!missing) return true;
            NbtCompound next = revised(index);
            NbtCompound pending = new NbtCompound();
            NbtList records = new NbtList();
            for (Entrada e : batch) {
                NbtCompound record = new NbtCompound();
                record.putLong("orden", e.orden());
                record.put("dato", e.dato().copy());
                records.add(record);
            }
            pending.put("registros", records);
            next.put("pendiente", pending);
            saveIndex(next);
            recover();
            return true;
        } catch (RuntimeException e) {
            MCA.LOGGER.warn("[Tropeles] cronica de {}: {}; se conserva el recuerdo reciente", owner, e.toString());
            return false;
        }
    }

    /** Newest first before an exclusive cursor. At most four pages and 256 entries per request, never a whole-life scan. */
    public List<Entrada> pagina(long before, int limit) {
        if (before <= 0 || limit <= 0 || siguiente() == 0) return List.of();
        int take = Math.min(PAGE_SIZE, limit);
        long last = Math.min(before - 1, siguiente() - 1);
        List<Entrada> result = new ArrayList<>();
        for (long number = last / PAGE_SIZE, count = 0; number >= 0 && count < READ_PAGES && result.size() < take; number--, count++) {
            NbtCompound entries = page(number).getCompound("datos");
            List<Long> ids = entries.getKeys().stream().map(Long::parseLong).filter(id -> id <= last)
                    .sorted(Comparator.reverseOrder()).toList();
            for (long id : ids) {
                result.add(new Entrada(id, entries.getCompound(Long.toString(id)).copy()));
                if (result.size() == take) break;
            }
        }
        return List.copyOf(result);
    }
}
