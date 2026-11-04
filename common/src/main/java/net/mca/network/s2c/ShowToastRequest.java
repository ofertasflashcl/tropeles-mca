package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.cobalt.network.Message;
import net.minecraft.text.Text;


public class ShowToastRequest implements Message {

    private final String title;
    private final String message;

    public ShowToastRequest(String title, String message) {
        this.title = title;
        this.message = message;
    }

    public Text getTitle() {
        return Text.translatable(title);
    }

    public Text getMessage() {
        return Text.translatable(message);
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleToastMessage(this);
    }

    private ShowToastRequest(net.minecraft.network.PacketByteBuf buffer) {
        this.title = net.mca.cobalt.network.PacketCodecs.string(buffer);
        this.message = net.mca.cobalt.network.PacketCodecs.string(buffer);
    }

    public static ShowToastRequest decode(net.minecraft.network.PacketByteBuf buffer) { return new ShowToastRequest(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        net.mca.cobalt.network.PacketCodecs.string(buffer, this.title);
        net.mca.cobalt.network.PacketCodecs.string(buffer, this.message);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
