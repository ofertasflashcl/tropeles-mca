package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.cobalt.network.Message;
import net.minecraft.text.Text;

import java.util.List;
import java.util.stream.Collectors;

public class CivilRegistryResponse implements Message {
    private final int index;
    private final List<String> lines;

    public CivilRegistryResponse(int index, List<Text> lines) {
        this.index = index;
        this.lines = lines.stream().map(Text.Serializer::toJson).collect(Collectors.toList());
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleCivilRegistryResponse(this);
    }

    public int getIndex() {
        return index;
    }

    public List<Text> getLines() {
        return lines.stream().map(Text.Serializer::fromJson).collect(Collectors.toList());
    }

    private CivilRegistryResponse(net.minecraft.network.PacketByteBuf buffer) {
        this.index = buffer.readInt();
        this.lines = net.mca.cobalt.network.PacketCodecs.json(buffer, new com.google.gson.reflect.TypeToken<List<String>>() {}.getType());
    }

    public static CivilRegistryResponse decode(net.minecraft.network.PacketByteBuf buffer) { return new CivilRegistryResponse(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        buffer.writeInt(this.index);
        net.mca.cobalt.network.PacketCodecs.json(buffer, this.lines, new com.google.gson.reflect.TypeToken<List<String>>() {}.getType());
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
