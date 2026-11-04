package net.mca.entity.ai.nucleo;

import net.mca.server.world.data.ArchivoTropeles;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Fixture invoked only by the operator-only in-game CI command. Exercises the actual bounded cache. */
public final class PruebasArchivo {
    private PruebasArchivo() { }
    private static UUID id(int i) { return UUID.nameUUIDFromBytes(("tropeles-archivo-ci-" + i).getBytes(StandardCharsets.UTF_8)); }

    public static int run(ServerWorld world, boolean prepare) {
        NucleoMundo store = NucleoMundo.get(world);
        int count = 0;
        for (int i = 0; i < 1000; i++) {
            Mente mind = store.of(id(i), world.getTime());
            String marker = "biografia-ci-" + i;
            if (prepare) {
                mind.dichos.add(marker);
                mind.sucio(world.getTime());
            }
            if (mind.dichos.contains(marker)) count++;
        }
        if (count != 1000) throw new IllegalStateException("Missing biographies: " + count + "/1000");
        if (store.vivas.size() > 256) throw new IllegalStateException("Unbounded live cache");
        int archived = -1;
        for (int i = 0; i < 1000; i++) {
            if (!store.vivas.containsKey(id(i)) && ArchivoTropeles.leer(world.getServer(), "nucleo", id(i)) != null) { archived = i; break; }
        }
        if (archived < 0) throw new IllegalStateException("No real archive eviction observed");
        if (!store.of(id(archived), world.getTime()).dichos.contains("biografia-ci-" + archived)) throw new IllegalStateException("Eviction lost biography");
        return count;
    }

    public static void revisions(ServerWorld world) throws java.io.IOException {
        UUID id = id(1001);
        NbtCompound newer = new NbtCompound(); newer.putLong("revision", 7); newer.putLong("tocado", 1); newer.putString("fact", "new");
        NbtCompound old = new NbtCompound(); old.putLong("revision", 6); old.putLong("tocado", Long.MAX_VALUE); old.putString("fact", "old");
        if (!ArchivoTropeles.guardar(world.getServer(), "prueba_revisiones", id, newer)) throw new IllegalStateException("Cannot save fixture");
        if (ArchivoTropeles.guardar(world.getServer(), "prueba_revisiones", id, old)) throw new IllegalStateException("Accepted stale write");
        if (!"new".equals(ArchivoTropeles.leer(world.getServer(), "prueba_revisiones", id).getString("fact"))) throw new IllegalStateException("Overwrote latest");
        if (ArchivoTropeles.masNuevo(old, newer) != newer) throw new IllegalStateException("Game clock won over revision");
        var file = world.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("tropeles/prueba_revisiones/" + id + ".nbt");
        byte[] original = java.nio.file.Files.readAllBytes(file);
        try {
            java.nio.file.Files.write(file, java.util.Arrays.copyOf(original, original.length / 2));
            if (ArchivoTropeles.leer(world.getServer(), "prueba_revisiones", id) != null) throw new IllegalStateException("Truncated archive accepted");
            if (ArchivoTropeles.masNuevo(newer, ArchivoTropeles.leer(world.getServer(), "prueba_revisiones", id)) != newer) throw new IllegalStateException("Good copy lost");
            if (ArchivoTropeles.guardar(world.getServer(), "prueba_revisiones", id, old)) throw new IllegalStateException("Unreadable archive silently replaced");
        } finally { java.nio.file.Files.write(file, original); }
    }
}
