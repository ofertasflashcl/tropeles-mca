package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.resources.data.skin.Clothing;
import net.mca.resources.data.skin.Hair;
import net.mca.resources.data.skin.SkinListEntry;
import net.mca.server.world.data.CustomClothingManager;
import net.minecraft.server.network.ServerPlayerEntity;


public class AddCustomClothingMessage implements Message {

    final SkinListEntry entry;

    public AddCustomClothingMessage(SkinListEntry entry) {
        this.entry = entry;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        if (!player.hasPermissionLevel(2) && !net.mca.Config.getInstance().allowEveryoneToAddContentGlobally) return;
        if (entry instanceof Clothing clothing) {
            CustomClothingManager.getClothing().addEntry(entry.getIdentifier(), clothing);
        } else if (entry instanceof Hair hair) {
            CustomClothingManager.getHair().addEntry(entry.getIdentifier(), hair);
        }
    }

    private AddCustomClothingMessage(net.minecraft.network.PacketByteBuf buffer) {
        this.entry = net.mca.cobalt.network.PacketCodecs.skin(buffer);
        if (this.entry == null) throw new io.netty.handler.codec.DecoderException("Missing entry");
    }

    public static AddCustomClothingMessage decode(net.minecraft.network.PacketByteBuf buffer) { return new AddCustomClothingMessage(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        net.mca.cobalt.network.PacketCodecs.skin(buffer, this.entry);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
