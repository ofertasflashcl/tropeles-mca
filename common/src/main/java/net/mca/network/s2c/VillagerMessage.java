package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.cobalt.network.Message;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.UUID;

public class VillagerMessage implements Message {

    private final String prefix;
    private final String message;
    private final UUID uuid;

    public VillagerMessage(MutableText prefix, MutableText message, UUID uuid) {
        this.prefix = Text.Serializer.toJson(prefix);
        this.message = Text.Serializer.toJson(message);
        this.uuid = uuid;
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleVillagerMessage(this);
    }

    public MutableText safeLoadFromJson(String json) {
        MutableText mutableText = Text.Serializer.fromJson(json);
        if (mutableText == null) return Text.literal("");
        return mutableText;
    }

    public MutableText getMessage() {
        return safeLoadFromJson(prefix).append(safeLoadFromJson(message));
    }

    public MutableText getContent() {
        return safeLoadFromJson(message);
    }

    public UUID getUuid() {
        return uuid;
    }

    private VillagerMessage(net.minecraft.network.PacketByteBuf buffer) {
        this.prefix = net.mca.cobalt.network.PacketCodecs.string(buffer);
        this.message = net.mca.cobalt.network.PacketCodecs.string(buffer);
        this.uuid = net.mca.cobalt.network.PacketCodecs.uuid(buffer);
    }

    public static VillagerMessage decode(net.minecraft.network.PacketByteBuf buffer) { return new VillagerMessage(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        net.mca.cobalt.network.PacketCodecs.string(buffer, this.prefix);
        net.mca.cobalt.network.PacketCodecs.string(buffer, this.message);
        net.mca.cobalt.network.PacketCodecs.uuid(buffer, this.uuid);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
