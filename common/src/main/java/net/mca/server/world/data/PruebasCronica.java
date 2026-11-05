package net.mca.server.world.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

/** Fault injection into the actual on-disk transaction, without a test-specific production write path. */
public final class PruebasCronica {
    private PruebasCronica() { }
    private static NbtCompound data(long i) { NbtCompound n = new NbtCompound(); n.putLong("recuerdo", i); return n; }

    public static String run(ServerWorld world) throws java.io.IOException {
        var server = world.getServer();
        String dim = "tropeles:cronica_ci";
        UUID id = UUID.nameUUIDFromBytes("cronica-transaccion-ci".getBytes(StandardCharsets.UTF_8));
        String kind = "cronica/v1/" + Base64.getUrlEncoder().withoutPadding().encodeToString(dim.getBytes(StandardCharsets.UTF_8));
        CronicaTropeles diary = new CronicaTropeles(server, dim, id);
        var batch = new ArrayList<CronicaTropeles.Entrada>();
        for (int i = 0; i < 256; i++) batch.add(new CronicaTropeles.Entrada(i, data(i)));
        if (!diary.guardar(batch)) throw new IllegalStateException("No guarda pagina inicial");
        // Crash point 1: index has the prepared records; page 1 has not been written yet.
        NbtCompound index = ArchivoTropeles.leer(server, kind + "/indice", id);
        NbtCompound pending = new NbtCompound();
        NbtList records = new NbtList();
        for (int i = 250; i < 270; i++) {
            NbtCompound e = new NbtCompound(); e.putLong("orden", i); e.put("dato", data(i)); records.add(e);
        }
        pending.put("registros", records);
        index.put("pendiente", pending);
        index.putLong("revision", index.getLong("revision") + 1);
        if (!ArchivoTropeles.guardar(server, kind + "/indice", id, index)) throw new IllegalStateException("No prepara transaccion");
        CronicaTropeles recovered = new CronicaTropeles(server, dim, id);
        if (recovered.siguiente() != 270 || recovered.pagina(270, 1).get(0).dato().getLong("recuerdo") != 269) throw new IllegalStateException("No recupera pagina pendiente");
        // Crash point 2: pages already contain every entry, but the index still says the batch is pending.
        index = ArchivoTropeles.leer(server, kind + "/indice", id);
        index.put("pendiente", pending.copy()); index.putLong("revision", index.getLong("revision") + 1);
        if (!ArchivoTropeles.guardar(server, kind + "/indice", id, index)) throw new IllegalStateException("No prepara repeticion");
        recovered = new CronicaTropeles(server, dim, id);
        if (recovered.siguiente() != 270 || recovered.pagina(1, 1).get(0).dato().getLong("recuerdo") != 0) throw new IllegalStateException("Repeticion no idempotente");
        if (recovered.guardar(0, data(999))) throw new IllegalStateException("Sobrescribe recuerdo de otra identidad");
        var file = server.getSavePath(WorldSavePath.ROOT).resolve("tropeles/" + kind + "/paginas/0/" + id + ".nbt");
        byte[] original = Files.readAllBytes(file);
        byte[] broken = Arrays.copyOf(original, original.length / 2);
        Files.write(file, broken);
        try {
            CronicaTropeles corrupt = new CronicaTropeles(server, dim, id);
            boolean refused = false;
            try { corrupt.pagina(1, 1); } catch (RuntimeException expected) { refused = true; }
            if (!refused || corrupt.guardar(0, data(0)) || !Arrays.equals(broken, Files.readAllBytes(file))) {
                throw new IllegalStateException("Corrupcion ignorada o sobrescrita");
            }
        } finally { Files.write(file, original); }
        CronicaTropeles left = new CronicaTropeles(server, "a:b_c", id);
        CronicaTropeles right = new CronicaTropeles(server, "a_b:c", id);
        if (!left.guardar(0, data(1)) || !right.guardar(0, data(2)) || left.pagina(1, 1).get(0).dato().equals(right.pagina(1, 1).get(0).dato())) {
            throw new IllegalStateException("Dimensiones con nombres parecidos comparten archivo");
        }
        return "replay antes/despues de pagina, conflicto y corrupcion rechazados, dimensiones sin alias";
    }
}
