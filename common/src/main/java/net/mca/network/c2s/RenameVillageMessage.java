package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.server.world.data.VillageManager;
import net.minecraft.server.network.ServerPlayerEntity;


public class RenameVillageMessage implements Message {

    private final int id;
    private final String name;

    public RenameVillageMessage(int id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        VillageManager.get(player.getServerWorld()).getOrEmpty(id).ifPresent(v -> v.setName(name));
    }

    private RenameVillageMessage(net.minecraft.network.PacketByteBuf buffer) {
        this.id = buffer.readInt();
        this.name = net.mca.cobalt.network.PacketCodecs.string(buffer);
        if (this.name == null) throw new io.netty.handler.codec.DecoderException("Missing name");
    }

    public static RenameVillageMessage decode(net.minecraft.network.PacketByteBuf buffer) { return new RenameVillageMessage(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        buffer.writeInt(this.id);
        net.mca.cobalt.network.PacketCodecs.string(buffer, this.name);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
