package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.VillagerEntityMCA;
import net.mca.network.s2c.InteractionDialogueResponse;
import net.mca.resources.Dialogues;
import net.mca.resources.data.dialogue.Question;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public class InteractionDialogueInitMessage implements Message {

    private final UUID villagerUUID;

    public InteractionDialogueInitMessage(UUID uuid) {
        villagerUUID = uuid;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        Entity v = player.getServerWorld().getEntity(villagerUUID);
        if (v instanceof VillagerEntityMCA villager) {
            if (!net.mca.entity.ai.chatAI.CommandPolicy.check(player, villager, "dialogue", net.mca.entity.ai.chatAI.CommandPolicy.Source.MENU).allowed()) return;
            Question question = Dialogues.getInstance().getQuestion("root");
            if (question.isAuto()) {
                Dialogues.getInstance().selectAnswer(villager, player, question.getName(), question.getRandomAnswer().getName());
            } else {
                NetworkHandler.sendToPlayer(new InteractionDialogueResponse(question, player, villager), player);
            }
        }
    }

    private InteractionDialogueInitMessage(net.minecraft.network.PacketByteBuf buffer) {
        this.villagerUUID = net.mca.cobalt.network.PacketCodecs.uuid(buffer);
        if (this.villagerUUID == null) throw new io.netty.handler.codec.DecoderException("Missing villagerUUID");
    }

    public static InteractionDialogueInitMessage decode(net.minecraft.network.PacketByteBuf buffer) { return new InteractionDialogueInitMessage(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        net.mca.cobalt.network.PacketCodecs.uuid(buffer, this.villagerUUID);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
