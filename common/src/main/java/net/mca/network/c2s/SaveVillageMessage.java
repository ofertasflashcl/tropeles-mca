package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.server.network.ServerPlayerEntity;


public class SaveVillageMessage implements Message {

    private final int id;
    private final float taxes;
    private final float populationThreshold;
    private final float marriageThreshold;

    public SaveVillageMessage(Village village) {
        this.id = village.getId();
        this.taxes = village.getTaxes();
        this.populationThreshold = village.getPopulationThreshold();
        this.marriageThreshold = village.getMarriageThreshold();
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        VillageManager.get(player.getServerWorld()).getOrEmpty(id).ifPresent(village -> {
            village.setTaxes(taxes);
            village.setPopulationThreshold(populationThreshold);
            village.setMarriageThreshold(marriageThreshold);
        });
    }

    private SaveVillageMessage(net.minecraft.network.PacketByteBuf buffer) {
        this.id = buffer.readInt();
        this.taxes = buffer.readFloat();
        this.populationThreshold = buffer.readFloat();
        this.marriageThreshold = buffer.readFloat();
    }

    public static SaveVillageMessage decode(net.minecraft.network.PacketByteBuf buffer) { return new SaveVillageMessage(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        buffer.writeInt(this.id);
        buffer.writeFloat(this.taxes);
        buffer.writeFloat(this.populationThreshold);
        buffer.writeFloat(this.marriageThreshold);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
