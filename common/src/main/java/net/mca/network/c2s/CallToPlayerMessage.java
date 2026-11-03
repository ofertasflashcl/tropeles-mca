package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public class CallToPlayerMessage implements Message {

    private final UUID uuid;

    public CallToPlayerMessage(UUID uuid) {
        this.uuid = uuid;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        Entity e = player.getServerWorld().getEntity(uuid);
        if (e instanceof VillagerEntityMCA v) {
            var permission = net.mca.entity.ai.chatAI.CommandPolicy.check(player, v, "call-player", net.mca.entity.ai.chatAI.CommandPolicy.Source.PACKET);
            if (!permission.allowed()) {
                net.mca.entity.ai.chatAI.CommandPolicy.report(v, "call-player", permission);
                net.mca.entity.ai.chatAI.ChatAI.deliver(player, v, permission.es());
                return;
            }
            net.mca.entity.ai.mente.Voluntad.nuevaGeneracion(v.getUuid());
            if (v.isSleeping()) {
                v.wakeUp();
            }
            v.stopRiding();
            v.setPosition(player.getX(), player.getY(), player.getZ());
        }
    }

    private CallToPlayerMessage(net.minecraft.network.PacketByteBuf buffer) {
        this.uuid = net.mca.cobalt.network.PacketCodecs.uuid(buffer);
        if (this.uuid == null) throw new io.netty.handler.codec.DecoderException("Missing uuid");
    }

    public static CallToPlayerMessage decode(net.minecraft.network.PacketByteBuf buffer) { return new CallToPlayerMessage(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        net.mca.cobalt.network.PacketCodecs.uuid(buffer, this.uuid);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}