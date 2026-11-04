package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.entity.VillagerLike;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public class InteractionVillagerMessage implements Message {

    private final String command;
    private final UUID villagerUUID;

    public InteractionVillagerMessage(String command, UUID villagerUUID) {
        this.command = command.replace("gui.button.", "");
        this.villagerUUID = villagerUUID;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        Entity v = player.getServerWorld().getEntity(villagerUUID);
        if (v instanceof VillagerLike<?> villager) {
            if (villager.getInteractions().handle(player, command)) {
                villager.getInteractions().stopInteracting();
            }
        }
    }

    private InteractionVillagerMessage(net.minecraft.network.PacketByteBuf buffer) {
        this.command = net.mca.cobalt.network.PacketCodecs.string(buffer);
        this.villagerUUID = net.mca.cobalt.network.PacketCodecs.uuid(buffer);
        if (this.command == null) throw new io.netty.handler.codec.DecoderException("Missing command");
        if (this.villagerUUID == null) throw new io.netty.handler.codec.DecoderException("Missing villagerUUID");
    }

    public static InteractionVillagerMessage decode(net.minecraft.network.PacketByteBuf buffer) { return new InteractionVillagerMessage(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        net.mca.cobalt.network.PacketCodecs.string(buffer, this.command);
        net.mca.cobalt.network.PacketCodecs.uuid(buffer, this.villagerUUID);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
