package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.network.NbtDataMessage;
import net.minecraft.nbt.NbtCompound;

import java.util.UUID;

public class PlayerDataMessage extends NbtDataMessage {

    public final UUID uuid;

    public PlayerDataMessage(UUID uuid, NbtCompound nbt) {
        super(nbt);
        this.uuid = uuid;
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handlePlayerDataMessage(this);
    }

    private PlayerDataMessage(net.minecraft.network.PacketByteBuf buffer) {
        super(net.mca.cobalt.network.PacketCodecs.nbt(buffer));
        this.uuid = net.mca.cobalt.network.PacketCodecs.uuid(buffer);
    }

    public static PlayerDataMessage decode(net.minecraft.network.PacketByteBuf buffer) { return new PlayerDataMessage(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        buffer.writeNbt(getData());
        net.mca.cobalt.network.PacketCodecs.uuid(buffer, this.uuid);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
