package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.CommonConfig;
import net.mca.cobalt.network.Message;


public class ConfigResponse implements Message {

    private final CommonConfig config;

    public ConfigResponse(CommonConfig config) {
        this.config = new CommonConfig(config);
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleConfigResponse(this);
    }

    public CommonConfig getConfig() {
        return config;
    }

    private ConfigResponse(net.minecraft.network.PacketByteBuf buffer) {
        this.config = net.mca.cobalt.network.PacketCodecs.json(buffer, CommonConfig.class);
    }

    public static ConfigResponse decode(net.minecraft.network.PacketByteBuf buffer) { return new ConfigResponse(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        net.mca.cobalt.network.PacketCodecs.json(buffer, this.config, CommonConfig.class);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
