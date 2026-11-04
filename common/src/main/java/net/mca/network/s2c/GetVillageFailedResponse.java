package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.cobalt.network.Message;


public class GetVillageFailedResponse implements Message {

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleVillageDataFailedResponse(this);
    }

    public GetVillageFailedResponse() { }

    private GetVillageFailedResponse(net.minecraft.network.PacketByteBuf buffer) {

    }

    public static GetVillageFailedResponse decode(net.minecraft.network.PacketByteBuf buffer) { return new GetVillageFailedResponse(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
