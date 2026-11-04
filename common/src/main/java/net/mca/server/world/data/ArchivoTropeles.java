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
 * then moved into place. A logical revision, independent of game time, orders records; unreadable and newer files
 * are never silently overwritten. Legacy records are compared by game time only until migrated.
 */
public final class ArchivoTropeles {
    private ArchivoTropeles() {
    }

    private static Path carpeta(MinecraftServer server, String kind) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("tropeles").resolve(kind.replaceAll("[^a-zA-Z0-9_\\-/]", "_"));
    }

    /** Writes {@code nbt} as the archived {@code kind} of {@code id}. False (and logged) when it could not be written. */
    public static synchronized boolean guardar(@Nullable MinecraftServer server, String kind, UUID id, NbtCompound nbt) {
        if (server == null) {
            return false;
        }
        Path dir = carpeta(server, kind);
        Path file = dir.resolve(id + ".nbt");
        Path tmp = dir.resolve(id + ".nbt.tmp");
        try {
            Files.createDirectories(dir);
            if (Files.exists(file)) {
                NbtCompound existing = read(file);
                int order = comparar(nbt, existing);
                NbtCompound a = nbt.copy(), b = existing.copy();
                a.remove("tocado"); b.remove("tocado");
                if (order < 0 || order == 0 && !a.equals(b)) {
                    MCA.LOGGER.error("[Tropeles] archivo {} {}: rechazada revisión {} sobre {} (obsoleta o en conflicto)",
                            kind, id, nbt.getLong("revision"), existing.getLong("revision"));
                    return false;
                }
                if (order == 0) return true;
            }
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp))))) {
                NbtIo.write(nbt, out);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException atomicRefused) {
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
    public static synchronized NbtCompound leer(@Nullable MinecraftServer server, String kind, UUID id) {
        if (server == null) {
            return null;
        }
        Path file = carpeta(server, kind).resolve(id + ".nbt");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return read(file);
        } catch (IOException | RuntimeException e) {
            MCA.LOGGER.warn("[Tropeles] no se pudo leer el archivo {} de {}: {}", kind, id, e.toString());
            return null;
        }
    }

    private static NbtCompound read(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
            NbtCompound result = NbtIo.read(in);
            // Drain to EOF so the gzip trailer/checksum is verified, even after the NBT root ended.
            if (result == null || in.read() != -1) throw new IOException("NBT vacío o datos adicionales");
            return result;
        }
    }

    public static int comparar(NbtCompound a, NbtCompound b) {
        boolean ar = a.contains("revision"), br = b.contains("revision");
        if (ar != br) return ar ? 1 : -1;
        return Long.compare(a.getLong(ar ? "revision" : "tocado"), b.getLong(br ? "revision" : "tocado"));
    }

    /** First revision for migrated records; positive revisions never depend on /time or day changes. */
    public static long revision(NbtCompound nbt) {
        return Math.max(1L, nbt.getLong("revision"));
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
        return comparar(archivado, guardado) > 0 ? archivado : guardado;
    }
}
