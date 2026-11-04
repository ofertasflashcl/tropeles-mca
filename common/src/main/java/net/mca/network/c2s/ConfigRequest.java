package net.mca.network.c2s;

import net.mca.Config;
import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.s2c.ConfigResponse;
import net.minecraft.server.network.ServerPlayerEntity;


public class ConfigRequest implements Message {

    @Override
    public void receive(ServerPlayerEntity player) {
        NetworkHandler.sendToPlayer(new ConfigResponse(Config.getInstance()), player);
    }

    public ConfigRequest() { }

    private ConfigRequest(net.minecraft.network.PacketByteBuf buffer) {

    }

    public static ConfigRequest decode(net.minecraft.network.PacketByteBuf buffer) { return new ConfigRequest(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
