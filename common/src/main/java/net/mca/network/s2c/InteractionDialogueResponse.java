package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.cobalt.network.Message;
import net.mca.entity.VillagerEntityMCA;
import net.mca.resources.data.dialogue.Question;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

public class InteractionDialogueResponse implements Message {

    public final String question;
    public final List<String> answers;

    public InteractionDialogueResponse(Question question, ServerPlayerEntity player, VillagerEntityMCA villager) {
        this.question = question.getName();
        this.answers = question.getValidAnswers(player, villager);
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleDialogueResponse(this);
    }

    private InteractionDialogueResponse(net.minecraft.network.PacketByteBuf buffer) {
        this.question = net.mca.cobalt.network.PacketCodecs.string(buffer);
        this.answers = net.mca.cobalt.network.PacketCodecs.json(buffer, new com.google.gson.reflect.TypeToken<List<String>>() {}.getType());
    }

    public static InteractionDialogueResponse decode(net.minecraft.network.PacketByteBuf buffer) { return new InteractionDialogueResponse(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        net.mca.cobalt.network.PacketCodecs.string(buffer, this.question);
        net.mca.cobalt.network.PacketCodecs.json(buffer, this.answers, new com.google.gson.reflect.TypeToken<List<String>>() {}.getType());
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
