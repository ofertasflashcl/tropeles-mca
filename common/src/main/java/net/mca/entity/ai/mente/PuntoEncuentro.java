package net.mca.entity.ai.mente;

import net.mca.entity.ai.body.Motor;
import net.mca.entity.ai.body.SondeoSalida;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** One agreed place, shared with the player who marked it, in the dimension where they stood. */
public final class PuntoEncuentro {
    private static final int VERSION = 1;

    @Nullable
    private Punto punto;
    @Nullable
    private NbtCompound versionFutura;

    private record Punto(UUID companero, Identifier dimension, BlockPos pies) {
    }

    public static boolean firme(ServerWorld world, BlockPos pies) {
        return SondeoSalida.seco(world, pies) && Motor.standCell(world, pies);
    }

    public boolean marcar(ServerWorld world, UUID companero, BlockPos pies) {
        if (versionFutura != null || !firme(world, pies)) {
            return false;
        }
        punto = new Punto(companero, world.getRegistryKey().getValue(), pies.toImmutable());
        return true;
    }

    @Nullable
    public BlockPos para(ServerWorld world, UUID companero) {
        return punto != null && punto.companero().equals(companero) && punto.dimension().equals(world.getRegistryKey().getValue())
                ? punto.pies() : null;
    }

    public NbtCompound guardar() {
        if (versionFutura != null) {
            return versionFutura.copy();
        }
        NbtCompound nbt = new NbtCompound();
        if (punto != null) {
            nbt.putInt("version", VERSION);
            nbt.putUuid("companero", punto.companero());
            nbt.putString("dimension", punto.dimension().toString());
            nbt.putLong("pies", punto.pies().asLong());
        }
        return nbt;
    }

    public void cargar(NbtCompound nbt) {
        punto = null;
        versionFutura = nbt.getInt("version") > VERSION ? nbt.copy() : null;
        if (nbt.getInt("version") == VERSION && nbt.containsUuid("companero") && nbt.contains("pies", NbtElement.LONG_TYPE)) {
            Identifier dimension = Identifier.tryParse(nbt.getString("dimension"));
            if (dimension != null && !nbt.getString("dimension").isEmpty()) {
                punto = new Punto(nbt.getUuid("companero"), dimension, BlockPos.fromLong(nbt.getLong("pies")));
            }
        }
    }
}
