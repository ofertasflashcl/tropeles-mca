package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.network.NbtDataMessage;
import net.minecraft.nbt.NbtCompound;


public class GetVillagerResponse extends NbtDataMessage {

    public GetVillagerResponse(NbtCompound data) {
        super(data);
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleVillagerDataResponse(this);
    }

    private GetVillagerResponse(net.minecraft.network.PacketByteBuf buffer) {
        super(net.mca.cobalt.network.PacketCodecs.nbt(buffer));
    }

    public static GetVillagerResponse decode(net.minecraft.network.PacketByteBuf buffer) { return new GetVillagerResponse(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        buffer.writeNbt(getData());
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
