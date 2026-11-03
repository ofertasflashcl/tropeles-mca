package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.cobalt.network.Message;
import net.mca.server.world.data.FamilyTreeNode;

import java.util.Map;
import java.util.UUID;

public class GetFamilyTreeResponse implements Message {

    public final UUID uuid;
    public final Map<UUID, FamilyTreeNode> family;

    public GetFamilyTreeResponse(UUID uuid, Map<UUID, FamilyTreeNode> family) {
        this.uuid = uuid;
        this.family = family;
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleFamilyTreeResponse(this);
    }

    private GetFamilyTreeResponse(net.minecraft.network.PacketByteBuf buffer) {
        this.uuid = net.mca.cobalt.network.PacketCodecs.uuid(buffer);
        this.family = net.mca.cobalt.network.PacketCodecs.json(buffer, new com.google.gson.reflect.TypeToken<Map<UUID, FamilyTreeNode>>() {}.getType());
    }

    public static GetFamilyTreeResponse decode(net.minecraft.network.PacketByteBuf buffer) { return new GetFamilyTreeResponse(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        net.mca.cobalt.network.PacketCodecs.uuid(buffer, this.uuid);
        net.mca.cobalt.network.PacketCodecs.json(buffer, this.family, new com.google.gson.reflect.TypeToken<Map<UUID, FamilyTreeNode>>() {}.getType());
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
