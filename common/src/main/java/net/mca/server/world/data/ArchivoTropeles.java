package net.mca.server.world.data;

import net.mca.MCA;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The durable archive of what each villager keeps (its mind, its memories, its lessons): one compressed NBT file per
 * villager and kind, at {@code <world>/tropeles/<kind>/<uuid>.nbt}. The caches in RAM and in the world's data files stay
 * bounded; what leaves them is written here instead of being forgotten, so no number of villagers makes one lose its
 * life, and it comes back the next time that villager is thought of. A file is written whole to a temporary name and
 * then moved into place, so a crash never leaves half of one. Each record carries {@code "tocado"} (world time of its
 * last change): when the world's data and the archive both hold a villager, the newer one wins ({@link #masNuevo}).
 */
public final class ArchivoTropeles {
    private ArchivoTropeles() {
    }

    private static Path carpeta(MinecraftServer server, String kind) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("tropeles").resolve(kind.replaceAll("[^a-zA-Z0-9_\\-/]", "_"));
    }

    /** Writes {@code nbt} as the archived {@code kind} of {@code id}. False (and logged) when it could not be written. */
    public static boolean guardar(@Nullable MinecraftServer server, String kind, UUID id, NbtCompound nbt) {
        if (server == null) {
            return false;
        }
        Path dir = carpeta(server, kind);
        Path file = dir.resolve(id + ".nbt");
        Path tmp = dir.resolve(id + ".nbt.tmp");
        try {
            Files.createDirectories(dir);
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp))))) {
                NbtIo.write(nbt, out);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicRefused) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            MCA.LOGGER.warn("[Tropeles] no se pudo archivar {} de {}: {}", kind, id, e.toString());
            return false;
        }
    }

    /** The archived {@code kind} of {@code id}, or null when there is none (or it could not be read, which is logged). */
    @Nullable
    public static NbtCompound leer(@Nullable MinecraftServer server, String kind, UUID id) {
        if (server == null) {
            return null;
        }
        Path file = carpeta(server, kind).resolve(id + ".nbt");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
            return NbtIo.read(in);
        } catch (IOException | RuntimeException e) {
            MCA.LOGGER.warn("[Tropeles] no se pudo leer el archivo {} de {}: {}", kind, id, e.toString());
            return null;
        }
    }

    /** Of what the world's data held and what the archive holds for one villager, the one changed last. */
    @Nullable
    public static NbtCompound masNuevo(@Nullable NbtCompound guardado, @Nullable NbtCompound archivado) {
        if (guardado == null) {
            return archivado;
        }
        if (archivado == null) {
            return guardado;
        }
        return archivado.getLong("tocado") > guardado.getLong("tocado") ? archivado : guardado;
    }
}
