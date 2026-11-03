package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.cobalt.network.Message;


public class BabyNameResponse implements Message {

    private final String name;

    public BabyNameResponse(String name) {
        this.name = name;
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleBabyNameResponse(this);
    }

    public String getName() {
        return name;
    }

    private BabyNameResponse(net.minecraft.network.PacketByteBuf buffer) {
        this.name = net.mca.cobalt.network.PacketCodecs.string(buffer);
    }

    public static BabyNameResponse decode(net.minecraft.network.PacketByteBuf buffer) { return new BabyNameResponse(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        net.mca.cobalt.network.PacketCodecs.string(buffer, this.name);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
