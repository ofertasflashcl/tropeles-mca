
package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.UUID;


public class SetTargetMessage implements Message {

    private final String itemIdentifier;
    private final String targetName;
    private final String targetUUID;

    public SetTargetMessage(Identifier identifier, String targetName, UUID targetUUID) {
        this.itemIdentifier = identifier.toString();
        this.targetName = targetName;
        this.targetUUID = targetUUID.toString();
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        Arrays.stream(Hand.values()).forEach(hand -> {
            ItemStack stack = player.getStackInHand(hand);
            if (Registries.ITEM.getId(stack.getItem()).toString().equals(itemIdentifier)) {
                stack.getOrCreateNbt().putString("targetName", targetName);
                stack.getOrCreateNbt().putUuid("targetUUID", UUID.fromString(targetUUID));
            }
        });
    }

    private SetTargetMessage(net.minecraft.network.PacketByteBuf buffer) {
        this.itemIdentifier = net.mca.cobalt.network.PacketCodecs.string(buffer);
        this.targetName = net.mca.cobalt.network.PacketCodecs.string(buffer);
        this.targetUUID = net.mca.cobalt.network.PacketCodecs.string(buffer);
        if (this.itemIdentifier == null) throw new io.netty.handler.codec.DecoderException("Missing itemIdentifier");
        if (this.targetName == null) throw new io.netty.handler.codec.DecoderException("Missing targetName");
        if (this.targetUUID == null) throw new io.netty.handler.codec.DecoderException("Missing targetUUID");
        try { UUID.fromString(this.targetUUID); new Identifier(this.itemIdentifier); }
        catch (IllegalArgumentException e) { throw new io.netty.handler.codec.DecoderException("Invalid target identity", e); }
    }

    public static SetTargetMessage decode(net.minecraft.network.PacketByteBuf buffer) { return new SetTargetMessage(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        net.mca.cobalt.network.PacketCodecs.string(buffer, this.itemIdentifier);
        net.mca.cobalt.network.PacketCodecs.string(buffer, this.targetName);
        net.mca.cobalt.network.PacketCodecs.string(buffer, this.targetUUID);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
