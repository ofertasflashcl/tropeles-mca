package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.cobalt.network.Message;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;


public class InteractionDialogueQuestionResponse implements Message {

    public final String questionText;
    public final boolean silent;

    public InteractionDialogueQuestionResponse(boolean silent, Text questionText) {
        this.questionText = Text.Serializer.toJson(questionText);
        this.silent = silent;
    }

    public MutableText getQuestionText() {
        return Text.Serializer.fromJson(questionText);
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleDialogueQuestionResponse(this);
    }

    private InteractionDialogueQuestionResponse(net.minecraft.network.PacketByteBuf buffer) {
        this.questionText = net.mca.cobalt.network.PacketCodecs.string(buffer);
        this.silent = buffer.readBoolean();
    }

    public static InteractionDialogueQuestionResponse decode(net.minecraft.network.PacketByteBuf buffer) { return new InteractionDialogueQuestionResponse(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        net.mca.cobalt.network.PacketCodecs.string(buffer, this.questionText);
        buffer.writeBoolean(this.silent);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
