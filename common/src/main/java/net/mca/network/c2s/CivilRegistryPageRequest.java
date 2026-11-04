package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.s2c.CivilRegistryResponse;
import net.mca.server.world.data.PlayerSaveData;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.List;

public class CivilRegistryPageRequest implements Message {

    private final int index;
    private final int from;
    private final int to;

    public CivilRegistryPageRequest(int index, int from, int to) {
        this.index = index;
        this.from = from;
        this.to = to;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        PlayerSaveData.get(player).getLastSeenVillage(VillageManager.get((ServerWorld)player.getWorld())).flatMap(Village::getCivilRegistry).ifPresentOrElse(c -> {
            List<Text> page = c.getPage(from, to);
            NetworkHandler.sendToPlayer(new CivilRegistryResponse(index, page), player);
        }, () -> {
            NetworkHandler.sendToPlayer(new CivilRegistryResponse(index, List.of(Text.translatable("civil_registry.empty"))), player);
        });
    }

    private CivilRegistryPageRequest(net.minecraft.network.PacketByteBuf buffer) {
        this.index = buffer.readInt();
        this.from = buffer.readInt();
        this.to = buffer.readInt();
    }

    public static CivilRegistryPageRequest decode(net.minecraft.network.PacketByteBuf buffer) { return new CivilRegistryPageRequest(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        buffer.writeInt(this.index);
        buffer.writeInt(this.from);
        buffer.writeInt(this.to);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
