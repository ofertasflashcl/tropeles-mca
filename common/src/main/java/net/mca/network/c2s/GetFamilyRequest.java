package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.VillagerLike;
import net.mca.network.s2c.GetFamilyResponse;
import net.mca.server.world.data.PlayerSaveData;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.stream.Stream;

public class GetFamilyRequest implements Message {

    @Override
    public void receive(ServerPlayerEntity player) {
        NbtCompound familyData = new NbtCompound();

        PlayerSaveData playerData = PlayerSaveData.get(player);

        //fetches all members
        //de-loaded members are excluded as they can't teleport anyway

        Stream.concat(
                        playerData.getFamilyEntry().getAllRelatives(4),
                        playerData.getPartnerUUID().stream()
                ).distinct()
                .map(player.getServerWorld()::getEntity)
                .filter(e -> e instanceof VillagerLike<?>)
                .limit(100)
                .forEach(e -> {
                    NbtCompound nbt = new NbtCompound();
                    ((MobEntity)e).writeCustomDataToNbt(nbt);
                    nbt.remove("Brain");
                    nbt.remove("memories");
                    nbt.remove("Inventory");
                    familyData.put(e.getUuid().toString(), nbt);
                });

        NetworkHandler.sendToPlayer(new GetFamilyResponse(familyData), player);
    }

    public GetFamilyRequest() { }

    private GetFamilyRequest(net.minecraft.network.PacketByteBuf buffer) {

    }

    public static GetFamilyRequest decode(net.minecraft.network.PacketByteBuf buffer) { return new GetFamilyRequest(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
