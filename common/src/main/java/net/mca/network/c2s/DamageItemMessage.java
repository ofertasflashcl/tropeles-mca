package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.util.Arrays;

public class DamageItemMessage implements Message {

    private final String itemIdentifier;

    public DamageItemMessage(Identifier identifier) {
        itemIdentifier = identifier.toString();
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        Arrays.stream(Hand.values()).forEach(hand -> {
            ItemStack stack = player.getStackInHand(hand);
            if (Registries.ITEM.getId(stack.getItem()).toString().equals(itemIdentifier)) {
                stack.damage(1, player, e -> e.sendEquipmentBreakStatus(EquipmentSlot.MAINHAND));
            }
        });
    }

    private DamageItemMessage(net.minecraft.network.PacketByteBuf buffer) {
        this.itemIdentifier = net.mca.cobalt.network.PacketCodecs.string(buffer);
        if (this.itemIdentifier == null) throw new io.netty.handler.codec.DecoderException("Missing itemIdentifier");
    }

    public static DamageItemMessage decode(net.minecraft.network.PacketByteBuf buffer) { return new DamageItemMessage(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        net.mca.cobalt.network.PacketCodecs.string(buffer, this.itemIdentifier);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
