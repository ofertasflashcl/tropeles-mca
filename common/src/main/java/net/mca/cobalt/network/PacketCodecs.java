package net.mca.cobalt.network;

import com.google.gson.*;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.mca.resources.data.analysis.*;
import net.mca.resources.data.skin.*;
import net.mca.resources.data.tasks.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import java.lang.reflect.Type;
import java.util.UUID;

/** Bounded wire values: no object streams or peer-selected Java class names. */
public final class PacketCodecs {
    public static final int VERSION = 2;
    public static final int MAX_PACKET = 2 * 1024 * 1024;
    private static final int MAX_JSON = 262144;
    private static final Gson GSON = new GsonBuilder().registerTypeAdapter(Task.class, new Tasks()).create();
    private PacketCodecs() { }
    public static String string(PacketByteBuf b) { return b.readBoolean() ? b.readString(4096) : null; }
    public static void string(PacketByteBuf b, String v) { b.writeBoolean(v != null); if (v != null) b.writeString(v, 4096); }
    public static UUID uuid(PacketByteBuf b) { return b.readBoolean() ? b.readUuid() : null; }
    public static void uuid(PacketByteBuf b, UUID v) { b.writeBoolean(v != null); if (v != null) b.writeUuid(v); }
    public static NbtCompound nbt(PacketByteBuf b) {
        NbtCompound n = b.readNbt();
        if (n == null) throw new DecoderException("Required NBT missing");
        return n;
    }
    public static int[] ints(PacketByteBuf b) { return b.readIntArray(65536); }
    public static void ints(PacketByteBuf b, int[] v) {
        if (v == null || v.length > 65536) throw new EncoderException("Array too large"); b.writeIntArray(v);
    }
    public static byte[] bytes(PacketByteBuf b) { return b.readByteArray(65536); }
    public static void bytes(PacketByteBuf b, byte[] v) {
        if (v == null || v.length > 65536) throw new EncoderException("Array too large"); b.writeByteArray(v);
    }
    public static <E extends Enum<E>> E enumeration(PacketByteBuf b, Class<E> type) {
        int i = b.readVarInt(); E[] values = type.getEnumConstants();
        if (i < 0 || i >= values.length) throw new DecoderException("Invalid enum value"); return values[i];
    }
    public static <T> T json(PacketByteBuf b, Type type) {
        if (!b.readBoolean()) return null;
        String text = b.readString(MAX_JSON);
        int depth = 0; boolean quoted = false, escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') { if (++depth > 32) throw new DecoderException("JSON nesting too deep"); }
            else if (c == '}' || c == ']') depth--;
        }
        try { return GSON.fromJson(text, type); }
        catch (RuntimeException e) { throw new DecoderException("Malformed typed JSON", e); }
    }
    public static void json(PacketByteBuf b, Object value, Type type) {
        b.writeBoolean(value != null); if (value != null) b.writeString(GSON.toJson(value, type), MAX_JSON);
    }
    public static SkinListEntry skin(PacketByteBuf b) {
        return switch (b.readUnsignedByte()) {
            case 0 -> json(b, Clothing.class); case 1 -> json(b, Hair.class);
            default -> throw new DecoderException("Unknown skin kind");
        };
    }
    public static void skin(PacketByteBuf b, SkinListEntry value) {
        if (value instanceof Clothing) { b.writeByte(0); json(b, value, Clothing.class); }
        else if (value instanceof Hair) { b.writeByte(1); json(b, value, Hair.class); }
        else throw new EncoderException("Unknown skin kind");
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Analysis<?> analysis(PacketByteBuf b) {
        int kind = b.readUnsignedByte();
        Analysis result = switch (kind) {
            case 0 -> new IntAnalysis(); case 1 -> new ChanceAnalysis(); case 2 -> new FloatAnalysis();
            default -> throw new DecoderException("Unknown analysis kind");
        };
        int count = b.readVarInt(); if (count < 0 || count > 1024) throw new DecoderException("Analysis too large");
        for (int i = 0; i < count; i++) {
            String key = b.readString(4096);
            if (kind == 2) result.add(key, b.readFloat()); else result.add(key, b.readInt());
        }
        return result;
    }
    public static void analysis(PacketByteBuf b, Analysis<?> value) {
        int kind = value instanceof ChanceAnalysis ? 1 : value instanceof IntAnalysis ? 0 : value instanceof FloatAnalysis ? 2 : -1;
        if (kind < 0 || value.getSummands().size() > 1024) throw new EncoderException("Unknown/oversized analysis");
        b.writeByte(kind); b.writeVarInt(value.getSummands().size());
        value.getSummands().forEach(pair -> {
            b.writeString(pair.getLeft(), 4096); Number n = (Number) pair.getRight();
            if (kind == 2) b.writeFloat(n.floatValue()); else b.writeInt(n.intValue());
        });
    }
    private static final class Tasks implements JsonSerializer<Task>, JsonDeserializer<Task> {
        private static final java.util.List<Class<? extends Task>> TYPES = java.util.List.of(AdvancementTask.class, BlockingTask.class,
                BuildingTask.class, PopulationTask.class, ReputationTask.class);
        @Override public JsonElement serialize(Task task, Type type, JsonSerializationContext ctx) {
            int kind = TYPES.indexOf(task.getClass()); if (kind < 0) throw new JsonParseException("Unknown task kind");
            JsonObject o = new JsonObject(); o.addProperty("kind", kind); o.add("value", ctx.serialize(task, TYPES.get(kind))); return o;
        }
        @Override public Task deserialize(JsonElement json, Type type, JsonDeserializationContext ctx) {
            JsonObject o = json.getAsJsonObject(); int kind = o.get("kind").getAsInt();
            if (kind < 0 || kind >= TYPES.size()) throw new JsonParseException("Unknown task kind");
            return ctx.deserialize(o.get("value"), TYPES.get(kind));
        }
    }
}
