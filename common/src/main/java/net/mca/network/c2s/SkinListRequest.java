package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.s2c.SkinListResponse;
import net.mca.resources.ClothingList;
import net.mca.resources.HairList;
import net.mca.resources.data.skin.Clothing;
import net.mca.resources.data.skin.Hair;
import net.mca.server.world.data.CustomClothingManager;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;

public class SkinListRequest implements Message {

    private <T> HashMap<String, T> merge(Map<String, T> a, Map<String, T> b) {
        HashMap<String, T> map = new HashMap<>();
        map.putAll(a);
        map.putAll(b);
        return map;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        Map<String, Clothing> clothing = CustomClothingManager.getClothing().getEntries();
        Map<String, Hair> hair = CustomClothingManager.getHair().getEntries();
        NetworkHandler.sendToPlayer(new SkinListResponse(merge(ClothingList.getInstance().clothing, clothing), merge(HairList.getInstance().hair, hair)), player);
    }

    public SkinListRequest() { }

    private SkinListRequest(net.minecraft.network.PacketByteBuf buffer) {

    }

    public static SkinListRequest decode(net.minecraft.network.PacketByteBuf buffer) { return new SkinListRequest(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
