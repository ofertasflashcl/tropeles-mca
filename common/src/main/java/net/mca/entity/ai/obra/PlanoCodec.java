package net.mca.entity.ai.obra;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** A local, bounded snapshot. No external schematic, entity NBT, raw registry ids or catalog lookup. */
public final class PlanoCodec {
    public static final int VERSION = 1;

    private PlanoCodec() {
    }

    public static NbtCompound guardar(Plano plano) {
        NbtCompound n = new NbtCompound();
        n.putInt("formato", VERSION);
        n.putString("id", plano.id);
        n.putInt("revision", plano.revision);
        n.putString("nombre", plano.nombre);
        n.putString("nombreEn", plano.nombreEn);
        n.putIntArray("centro", coordenadas(plano.centro));
        n.putIntArray("entrada", coordenadas(plano.entrada));
        n.putString("hash", plano.hash());
        NbtList list = new NbtList();
        for (Plano.Pieza p : plano.piezas) {
            NbtCompound c = new NbtCompound();
            c.putIntArray("pos", coordenadas(p.rel()));
            c.putString("rol", p.rol().name());
            c.putString("bloque", Registries.BLOCK.getId(p.state().getBlock()).toString());
            NbtCompound props = new NbtCompound();
            propiedades(p.state()).forEach(props::putString);
            c.put("propiedades", props);
            list.add(c);
        }
        n.put("piezas", list);
        return n;
    }

    /** Rejects unknown schema, blocks, properties and content changes; never substitutes air or a catalog design. */
    public static Plano leer(NbtCompound n) {
        exigir(n, "formato", NbtElement.INT_TYPE);
        if (n.getInt("formato") != VERSION) {
            throw new IllegalArgumentException("Unsupported blueprint snapshot version");
        }
        for (String key : List.of("id", "nombre", "nombreEn", "hash")) {
            exigir(n, key, NbtElement.STRING_TYPE);
        }
        exigir(n, "revision", NbtElement.INT_TYPE);
        exigir(n, "piezas", NbtElement.LIST_TYPE);
        NbtList list = n.getList("piezas", NbtElement.COMPOUND_TYPE);
        if (list.isEmpty() || list.size() > Plano.MAX_PIEZAS) {
            throw new IllegalArgumentException("Invalid blueprint snapshot cell count");
        }
        List<Plano.Pieza> piezas = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            exigir(c, "rol", NbtElement.STRING_TYPE);
            exigir(c, "bloque", NbtElement.STRING_TYPE);
            exigir(c, "propiedades", NbtElement.COMPOUND_TYPE);
            Identifier id = Identifier.tryParse(c.getString("bloque"));
            if (id == null || !Registries.BLOCK.containsId(id)) {
                throw new IllegalArgumentException("Unknown blueprint block: " + c.getString("bloque"));
            }
            BlockState state = Registries.BLOCK.get(id).getDefaultState();
            NbtCompound props = c.getCompound("propiedades");
            for (String key : props.getKeys()) {
                exigir(props, key, NbtElement.STRING_TYPE);
                state = propiedad(state, key, props.getString(key));
            }
            piezas.add(new Plano.Pieza(pos(c, "pos"), state, Plano.Rol.valueOf(c.getString("rol"))));
        }
        Plano plano = Plano.crear(n.getString("id"), n.getInt("revision"), n.getString("nombre"), n.getString("nombreEn"),
                pos(n, "centro"), pos(n, "entrada"), piezas);
        if (!plano.hash().equals(n.getString("hash"))) {
            throw new IllegalArgumentException("Blueprint snapshot hash mismatch");
        }
        return plano;
    }

    static BlockState propiedad(BlockState state, String key, String value) {
        Property<?> prop = state.getBlock().getStateManager().getProperty(key);
        if (prop == null) {
            throw new IllegalArgumentException("Unknown blueprint property: " + key);
        }
        return aplicar(state, prop, value);
    }

    private static <T extends Comparable<T>> BlockState aplicar(BlockState state, Property<T> property, String value) {
        return state.with(property, property.parse(value).orElseThrow(() -> new IllegalArgumentException("Invalid blueprint property value")));
    }

    private static int[] coordenadas(BlockPos p) {
        return new int[]{p.getX(), p.getY(), p.getZ()};
    }

    private static BlockPos pos(NbtCompound n, String key) {
        exigir(n, key, NbtElement.INT_ARRAY_TYPE);
        int[] at = n.getIntArray(key);
        if (at.length != 3) {
            throw new IllegalArgumentException("Invalid blueprint position: " + key);
        }
        return new BlockPos(at[0], at[1], at[2]);
    }

    private static void exigir(NbtCompound n, String key, int type) {
        if (!n.contains(key, type)) {
            throw new IllegalArgumentException("Missing/invalid blueprint field: " + key);
        }
    }

    private static Map<String, String> propiedades(BlockState state) {
        Map<String, String> props = new TreeMap<>();
        for (Property<?> p : state.getProperties()) {
            props.put(p.getName(), valor(state, p));
        }
        return props;
    }

    private static <T extends Comparable<T>> String valor(BlockState state, Property<T> p) {
        return p.name(state.get(p));
    }

    /** Canonical geometry identity, independent of input cell/property iteration order. Not an approval token. */
    static String hash(Plano plano) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(VERSION);
                out.writeUTF(plano.id);
                out.writeInt(plano.revision);
                out.writeUTF(plano.nombre);
                out.writeUTF(plano.nombreEn);
                escribirPos(out, plano.centro);
                escribirPos(out, plano.entrada);
                List<Plano.Pieza> sorted = new ArrayList<>(plano.piezas);
                sorted.sort(Comparator.comparingInt((Plano.Pieza p) -> p.rel().getX()).thenComparingInt(p -> p.rel().getY()).thenComparingInt(p -> p.rel().getZ()));
                out.writeInt(sorted.size());
                for (Plano.Pieza p : sorted) {
                    escribirPos(out, p.rel());
                    out.writeUTF(p.rol().name());
                    out.writeUTF(Registries.BLOCK.getId(p.state().getBlock()).toString());
                    Map<String, String> props = propiedades(p.state());
                    out.writeInt(props.size());
                    for (Map.Entry<String, String> prop : props.entrySet()) {
                        out.writeUTF(prop.getKey());
                        out.writeUTF(prop.getValue());
                    }
                }
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot identify blueprint snapshot", e);
        }
    }

    private static void escribirPos(DataOutputStream out, BlockPos p) throws IOException {
        out.writeInt(p.getX());
        out.writeInt(p.getY());
        out.writeInt(p.getZ());
    }
}
