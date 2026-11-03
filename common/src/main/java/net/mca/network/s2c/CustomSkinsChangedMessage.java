package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.cobalt.network.Message;


public class CustomSkinsChangedMessage implements Message {

    public CustomSkinsChangedMessage() {
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleCustomSkinsChangedMessage(this);
    }

    private CustomSkinsChangedMessage(net.minecraft.network.PacketByteBuf buffer) {

    }

    public static CustomSkinsChangedMessage decode(net.minecraft.network.PacketByteBuf buffer) { return new CustomSkinsChangedMessage(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
