package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.item.BabyItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;


public class BabyNamingVillagerMessage implements Message {

    private final int slot;
    private final String name;

    public BabyNamingVillagerMessage(int slot, String name) {
        this.slot = slot;
        this.name = name;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        ItemStack stack = player.getInventory().getStack(slot);
        BabyItem.getBabyNbt(stack).putString("babyName", name);
    }

    private BabyNamingVillagerMessage(net.minecraft.network.PacketByteBuf buffer) {
        this.slot = buffer.readInt();
        this.name = net.mca.cobalt.network.PacketCodecs.string(buffer);
        if (this.name == null) throw new io.netty.handler.codec.DecoderException("Missing name");
    }

    public static BabyNamingVillagerMessage decode(net.minecraft.network.PacketByteBuf buffer) { return new BabyNamingVillagerMessage(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        buffer.writeInt(this.slot);
        net.mca.cobalt.network.PacketCodecs.string(buffer, this.name);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
