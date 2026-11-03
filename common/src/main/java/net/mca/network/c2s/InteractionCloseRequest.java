package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public class InteractionCloseRequest implements Message {

    private final UUID villagerUUID;

    public InteractionCloseRequest(UUID uuid) {
        villagerUUID = uuid;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        Entity v = player.getServerWorld().getEntity(villagerUUID);
        if (v instanceof VillagerEntityMCA villager && villager.getInteractions().getInteractingPlayer().filter(p -> p == player).isPresent()) {
            villager.getInteractions().stopInteracting();
        }
    }

    private InteractionCloseRequest(net.minecraft.network.PacketByteBuf buffer) {
        this.villagerUUID = net.mca.cobalt.network.PacketCodecs.uuid(buffer);
        if (this.villagerUUID == null) throw new io.netty.handler.codec.DecoderException("Missing villagerUUID");
    }

    public static InteractionCloseRequest decode(net.minecraft.network.PacketByteBuf buffer) { return new InteractionCloseRequest(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        net.mca.cobalt.network.PacketCodecs.uuid(buffer, this.villagerUUID);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
