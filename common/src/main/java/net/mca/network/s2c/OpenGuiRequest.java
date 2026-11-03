package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.cobalt.network.Message;
import net.minecraft.entity.Entity;


public class OpenGuiRequest implements Message {

    public final int gui;

    public final int villager;

    public OpenGuiRequest(OpenGuiRequest.Type gui, Entity villager) {
        this(gui, villager.getId());
    }

    public OpenGuiRequest(OpenGuiRequest.Type gui, int villager) {
        this.gui = gui.ordinal();
        this.villager = villager;
    }

    public OpenGuiRequest(OpenGuiRequest.Type gui) {
        this(gui, 0);
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleGuiRequest(this);
    }

    public Type getGui() {
        return Type.values()[gui];
    }

    public enum Type {
        BABY_NAME,
        WHISTLE,
        BLUEPRINT,
        INTERACT,
        VILLAGER_EDITOR,
        LIMITED_VILLAGER_EDITOR,
        BOOK,
        FAMILY_TREE,
        VILLAGER_TRACKER,
        NEEDLE_AND_THREAD,
        COMB,
        CLOSE,
    }

    private OpenGuiRequest(net.minecraft.network.PacketByteBuf buffer) {
        this.gui = buffer.readInt();
        this.villager = buffer.readInt();
    }

    public static OpenGuiRequest decode(net.minecraft.network.PacketByteBuf buffer) { return new OpenGuiRequest(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        buffer.writeInt(this.gui);
        buffer.writeInt(this.villager);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
