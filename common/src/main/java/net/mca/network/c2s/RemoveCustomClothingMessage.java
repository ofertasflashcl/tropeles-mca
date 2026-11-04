package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.server.world.data.CustomClothingManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;


public class RemoveCustomClothingMessage implements Message {

    final Type type;
    final String identifier;

    public RemoveCustomClothingMessage(Type type, Identifier identifier) {
        this.type = type;
        this.identifier = String.valueOf(identifier);
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        if (!player.hasPermissionLevel(2) && !net.mca.Config.getInstance().allowEveryoneToAddContentGlobally) return;
        if (type == Type.CLOTHING) {
            CustomClothingManager.getClothing().removeEntry(identifier);
        } else if (type == Type.HAIR) {
            CustomClothingManager.getHair().removeEntry(identifier);
        }
    }

    public enum Type {
        CLOTHING,
        HAIR
    }

    private RemoveCustomClothingMessage(net.minecraft.network.PacketByteBuf buffer) {
        this.type = net.mca.cobalt.network.PacketCodecs.enumeration(buffer, Type.class);
        this.identifier = net.mca.cobalt.network.PacketCodecs.string(buffer);
        if (this.type == null) throw new io.netty.handler.codec.DecoderException("Missing type");
        if (this.identifier == null) throw new io.netty.handler.codec.DecoderException("Missing identifier");
    }

    public static RemoveCustomClothingMessage decode(net.minecraft.network.PacketByteBuf buffer) { return new RemoveCustomClothingMessage(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        buffer.writeVarInt(this.type.ordinal());
        net.mca.cobalt.network.PacketCodecs.string(buffer, this.identifier);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
