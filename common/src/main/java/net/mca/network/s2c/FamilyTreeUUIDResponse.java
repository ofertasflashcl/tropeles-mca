package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.client.gui.FamilyTreeSearchScreen;
import net.mca.cobalt.network.Message;

import java.util.List;

public class FamilyTreeUUIDResponse implements Message {

    private final List<FamilyTreeSearchScreen.Entry> list;

    public FamilyTreeUUIDResponse(List<FamilyTreeSearchScreen.Entry> list) {
        this.list = list;
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleFamilyTreeUUIDResponse(this);
    }

    public List<FamilyTreeSearchScreen.Entry> getList() {
        return list;
    }

    private FamilyTreeUUIDResponse(net.minecraft.network.PacketByteBuf buffer) {
        this.list = net.mca.cobalt.network.PacketCodecs.json(buffer, new com.google.gson.reflect.TypeToken<List<FamilyTreeSearchScreen.Entry>>() {}.getType());
    }

    public static FamilyTreeUUIDResponse decode(net.minecraft.network.PacketByteBuf buffer) { return new FamilyTreeUUIDResponse(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        net.mca.cobalt.network.PacketCodecs.json(buffer, this.list, new com.google.gson.reflect.TypeToken<List<FamilyTreeSearchScreen.Entry>>() {}.getType());
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
