package net.mca.server.command;

import io.netty.buffer.Unpooled;
import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.PacketCodecs;
import net.mca.network.c2s.*;
import net.mca.network.s2c.*;
import net.minecraft.network.PacketByteBuf;
import java.util.UUID;
import java.util.function.Function;

/** Wire tests run on a dedicated server: encoding must never load a client handler. */
final class PruebasPaquetes {
    private static <T extends Message> T roundTrip(T value, Class<T> type, Function<PacketByteBuf, T> decoder) {
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        PacketByteBuf encoded = new PacketByteBuf(Unpooled.buffer());
        try {
            value.encode(buffer);
            byte[] before = new byte[buffer.readableBytes()]; buffer.getBytes(0, before);
            T read = Message.decode(buffer, type, decoder);
            read.encode(encoded);
            byte[] after = new byte[encoded.readableBytes()]; encoded.getBytes(0, after);
            if (!java.util.Arrays.equals(before, after)) throw new IllegalStateException("Round-trip changed " + type.getSimpleName());
            return read;
        } finally { buffer.release(); encoded.release(); }
    }
    private static void reject(java.util.function.Consumer<PacketByteBuf> write, Class<InteractionVillagerMessage> type) {
        PacketByteBuf b = new PacketByteBuf(Unpooled.buffer());
        try {
            write.accept(b);
            try { Message.decode(b, type, InteractionVillagerMessage::decode); }
            catch (RuntimeException expected) { return; }
            throw new IllegalStateException("Malformed packet accepted");
        } finally { b.release(); }
    }

    static void run() {
        UUID id = UUID.fromString("10000000-0000-0000-0000-000000000001");
        roundTrip(new InteractionVillagerMessage("follow", id), InteractionVillagerMessage.class, InteractionVillagerMessage::decode);
        roundTrip(new GetFamilyRequest(), GetFamilyRequest.class, GetFamilyRequest::decode);
        roundTrip(new BabyNameRequest(net.mca.entity.ai.relationship.Gender.MALE), BabyNameRequest.class, BabyNameRequest::decode);
        var nbt = new net.minecraft.nbt.NbtCompound(); nbt.putString("test", "persistente");
        var data = roundTrip(new PlayerDataMessage(id, nbt), PlayerDataMessage.class, PlayerDataMessage::decode);
        if (!nbt.equals(data.getData()) || !id.equals(data.uuid)) throw new IllegalStateException("NBT/UUID changed");
        var config = new net.mca.CommonConfig(); config.babyItemGrowUpTime = 12345;
        if (roundTrip(new ConfigResponse(config), ConfigResponse.class, ConfigResponse::decode).getConfig().babyItemGrowUpTime != 12345)
            throw new IllegalStateException("Config changed");
        var analysis = new net.mca.resources.data.analysis.FloatAnalysis(); analysis.add("test", 0.25F);
        if (!roundTrip(new AnalysisResults(analysis), AnalysisResults.class, AnalysisResults::decode).analysis.getTotal().equals(0.25F))
            throw new IllegalStateException("Analysis changed");
        roundTrip(new FantasmaMessage("test", "minecraft:overworld", "Prueba", new int[]{1}, new int[]{2}, new int[]{3}, new int[]{4}, new byte[]{0}),
                FantasmaMessage.class, FantasmaMessage::decode);
        var family = new java.util.HashMap<UUID, net.mca.server.world.data.FamilyTreeNode>();
        family.put(id, new net.mca.server.world.data.FamilyTreeNode(null, id, "Prueba", false,
                net.mca.entity.ai.relationship.Gender.MALE, net.minecraft.util.Util.NIL_UUID, net.minecraft.util.Util.NIL_UUID));
        roundTrip(new GetFamilyTreeResponse(id, family), GetFamilyTreeResponse.class, GetFamilyTreeResponse::decode);
        PacketByteBuf json = new PacketByteBuf(Unpooled.buffer());
        try {
            var type = new com.google.gson.reflect.TypeToken<java.util.Map<net.mca.resources.Rank, java.util.List<net.mca.resources.data.tasks.Task>>>() {}.getType();
            java.util.Map<net.mca.resources.Rank, java.util.List<net.mca.resources.data.tasks.Task>> tasks = java.util.Map.of(
                    net.mca.resources.Rank.PEASANT, java.util.List.of(new net.mca.resources.data.tasks.BuildingTask("house")));
            PacketCodecs.json(json, tasks, type);
            java.util.Map<net.mca.resources.Rank, java.util.List<net.mca.resources.data.tasks.Task>> read = PacketCodecs.json(json, type);
            if (!(read.get(net.mca.resources.Rank.PEASANT).get(0) instanceof net.mca.resources.data.tasks.BuildingTask)) throw new IllegalStateException("Task subtype lost");
        } finally { json.release(); }
        reject(b -> b.writeBytes(new byte[]{(byte)0xac,(byte)0xed,0,5}), InteractionVillagerMessage.class);
        reject(b -> b.writeVarInt(PacketCodecs.VERSION), InteractionVillagerMessage.class);
        reject(b -> b.writeVarInt(999), InteractionVillagerMessage.class);
        reject(b -> b.writeZero(PacketCodecs.MAX_PACKET + 1), InteractionVillagerMessage.class);
        reject(b -> { new InteractionVillagerMessage("follow", id).encode(b); b.writeByte(1); }, InteractionVillagerMessage.class);
        PacketByteBuf badEnum = new PacketByteBuf(Unpooled.buffer());
        try {
            badEnum.writeVarInt(PacketCodecs.VERSION); badEnum.writeVarInt(9999);
            boolean denied = false;
            try { Message.decode(badEnum, BabyNameRequest.class, BabyNameRequest::decode); } catch (RuntimeException e) { denied = true; }
            if (!denied) throw new IllegalStateException("Invalid enum accepted");
        } finally { badEnum.release(); }
    }
}
