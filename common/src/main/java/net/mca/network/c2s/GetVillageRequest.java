package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.s2c.GetVillageFailedResponse;
import net.mca.network.s2c.GetVillageResponse;
import net.mca.resources.Rank;
import net.mca.resources.Tasks;
import net.mca.server.world.data.GraveyardManager;
import net.mca.server.world.data.Village;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Optional;
import java.util.Set;

public class GetVillageRequest implements Message {

    @Override
    public void receive(ServerPlayerEntity player) {
        Optional<Village> village = Village.findNearest(player);
        if (village.isPresent()) {
            GraveyardManager.get(player.getServerWorld()).reportToVillageManager(player);
            village.get().updateMaxPopulation();
            int reputation = village.get().getReputation(player);
            boolean isVillage = village.get().isVillage();
            Rank rank = Tasks.getRank(village.get(), player);
            Set<String> ids = Tasks.getCompletedIds(village.get(), player);
            NetworkHandler.sendToPlayer(new GetVillageResponse(village.get(), rank, reputation, isVillage, ids), player);
        } else {
            NetworkHandler.sendToPlayer(new GetVillageFailedResponse(), player);
        }
    }

    public GetVillageRequest() { }

    private GetVillageRequest(net.minecraft.network.PacketByteBuf buffer) {

    }

    public static GetVillageRequest decode(net.minecraft.network.PacketByteBuf buffer) { return new GetVillageRequest(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
