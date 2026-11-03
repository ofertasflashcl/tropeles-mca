package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.Config;
import net.mca.cobalt.network.Message;
import net.minecraft.server.network.ServerPlayerEntity;


public class OpenDestinyGuiRequest implements Message {

    public final int player;
    public final boolean allowTeleportation;

    public OpenDestinyGuiRequest(ServerPlayerEntity player) {
        this.player = player.getId();
        this.allowTeleportation = Config.getInstance().allowDestinyTeleportation;
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleDestinyGuiRequest(this);
    }

    private OpenDestinyGuiRequest(net.minecraft.network.PacketByteBuf buffer) {
        this.player = buffer.readInt();
        this.allowTeleportation = buffer.readBoolean();
    }

    public static OpenDestinyGuiRequest decode(net.minecraft.network.PacketByteBuf buffer) { return new OpenDestinyGuiRequest(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        buffer.writeInt(this.player);
        buffer.writeBoolean(this.allowTeleportation);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
