package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.s2c.PlayerDataMessage;
import net.mca.server.world.data.PlayerSaveData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public class PlayerDataRequest implements Message {

    private final UUID uuid;

    public PlayerDataRequest(UUID uuid) {
        this.uuid = uuid;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        PlayerEntity playerEntity = player.getWorld().getPlayerByUuid(uuid);
        if (playerEntity instanceof ServerPlayerEntity serverPlayerEntity) {
            PlayerSaveData data = PlayerSaveData.get(serverPlayerEntity);
            if (data.isEntityDataSet()) {
                NbtCompound nbt = data.getEntityData();
                NetworkHandler.sendToPlayer(new PlayerDataMessage(uuid, nbt), player);
            }
        }
    }

    private PlayerDataRequest(net.minecraft.network.PacketByteBuf buffer) {
        this.uuid = net.mca.cobalt.network.PacketCodecs.uuid(buffer);
        if (this.uuid == null) throw new io.netty.handler.codec.DecoderException("Missing uuid");
    }

    public static PlayerDataRequest decode(net.minecraft.network.PacketByteBuf buffer) { return new PlayerDataRequest(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        net.mca.cobalt.network.PacketCodecs.uuid(buffer, this.uuid);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
