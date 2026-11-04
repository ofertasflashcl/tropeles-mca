package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.entity.VillagerEntityMCA;
import net.mca.resources.Dialogues;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public class InteractionDialogueMessage implements Message {

    private final UUID villagerUUID;
    private final String question;
    private final String answer;

    public InteractionDialogueMessage(UUID uuid, String question, String answer) {
        villagerUUID = uuid;
        this.question = question;
        this.answer = answer;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        Entity v = player.getServerWorld().getEntity(villagerUUID);
        if (v instanceof VillagerEntityMCA villager) {
            Dialogues.getInstance().selectAnswer(villager, player, question, answer);
        }
    }

    private InteractionDialogueMessage(net.minecraft.network.PacketByteBuf buffer) {
        this.villagerUUID = net.mca.cobalt.network.PacketCodecs.uuid(buffer);
        this.question = net.mca.cobalt.network.PacketCodecs.string(buffer);
        this.answer = net.mca.cobalt.network.PacketCodecs.string(buffer);
        if (this.villagerUUID == null) throw new io.netty.handler.codec.DecoderException("Missing villagerUUID");
        if (this.question == null) throw new io.netty.handler.codec.DecoderException("Missing question");
        if (this.answer == null) throw new io.netty.handler.codec.DecoderException("Missing answer");
    }

    public static InteractionDialogueMessage decode(net.minecraft.network.PacketByteBuf buffer) { return new InteractionDialogueMessage(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        net.mca.cobalt.network.PacketCodecs.uuid(buffer, this.villagerUUID);
        net.mca.cobalt.network.PacketCodecs.string(buffer, this.question);
        net.mca.cobalt.network.PacketCodecs.string(buffer, this.answer);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
