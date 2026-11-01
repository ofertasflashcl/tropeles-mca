package net.mca.entity.ai.obra;

import net.mca.util.WorldUtils;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The buildings under way in one world (dimension), kept with it: a relog or a restart takes nobody's house away. */
public final class ObrasMundo extends PersistentState {
    private final Map<String, Obra> obras = new LinkedHashMap<>();

    public static ObrasMundo get(ServerWorld world) {
        return WorldUtils.loadData(world, ObrasMundo::new, w -> new ObrasMundo(), "mca_tropeles_obras");
    }

    ObrasMundo() {
    }

    ObrasMundo(NbtCompound nbt) {
        NbtList list = nbt.getList("obras", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            Obra o = Obra.leer(list.getCompound(i));
            if (o != null) {
                obras.put(o.id, o);
            }
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.put("obras", Obra.lista(obras.values()));
        return nbt;
    }

    public Collection<Obra> todas() {
        return Collections.unmodifiableCollection(obras.values());
    }

    /** The building this villager is putting up, or null. */
    @Nullable
    public Obra de(UUID constructor) {
        for (Obra o : obras.values()) {
            if (o.constructor.equals(constructor)) {
                return o;
            }
        }
        return null;
    }

    @Nullable
    public Obra porId(String id) {
        return obras.get(id);
    }

    public void poner(Obra o) {
        obras.put(o.id, o);
        markDirty();
    }

    public void quitar(Obra o) {
        obras.remove(o.id);
        markDirty();
    }

    /** Something about one of them changed (a scaffold, a pause): saved with the world. */
    public void cambio() {
        markDirty();
    }

    /** Another building's box (with a gap of {@code margen}) overlaps this one: two houses never share a wall by accident. */
    public boolean choca(BlockPos min, BlockPos max, int margen, @Nullable String salvo) {
        for (Obra o : obras.values()) {
            if (o.id.equals(salvo)) {
                continue;
            }
            BlockPos a = o.min();
            BlockPos b = o.max();
            if (min.getX() <= b.getX() + margen && max.getX() >= a.getX() - margen && min.getZ() <= b.getZ() + margen && max.getZ() >= a.getZ() - margen
                    && min.getY() <= b.getY() + margen && max.getY() >= a.getY() - margen) {
                return true;
            }
        }
        return false;
    }

    /** The buildings within {@code radio} blocks of {@code pos}. */
    public List<Obra> cerca(BlockPos pos, double radio) {
        List<Obra> list = new ArrayList<>();
        for (Obra o : obras.values()) {
            if (o.centro().isWithinDistance(pos, radio)) {
                list.add(o);
            }
        }
        return list;
    }

    /** Any building with a block or an empty cell at {@code pos}. */
    @Nullable
    public Obra en(BlockPos pos) {
        for (Obra o : obras.values()) {
            if (o.cerca(pos, 0) && o.ocupa(pos)) {
                return o;
            }
        }
        return null;
    }
}
