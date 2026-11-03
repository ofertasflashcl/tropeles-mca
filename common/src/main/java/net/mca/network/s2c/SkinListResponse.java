package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.cobalt.network.Message;
import net.mca.resources.data.skin.Clothing;
import net.mca.resources.data.skin.Hair;

import java.util.HashMap;

public class SkinListResponse implements Message {

    private final HashMap<String, Clothing> clothing;
    private final HashMap<String, Hair> hair;

    public SkinListResponse(HashMap<String, Clothing> clothing, HashMap<String, Hair> hair) {
        this.clothing = clothing;
        this.hair = hair;
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleSkinListResponse(this);
    }

    public HashMap<String, Clothing> getClothing() {
        return clothing;
    }

    public HashMap<String, Hair> getHair() {
        return hair;
    }

    private SkinListResponse(net.minecraft.network.PacketByteBuf buffer) {
        this.clothing = net.mca.cobalt.network.PacketCodecs.json(buffer, new com.google.gson.reflect.TypeToken<HashMap<String, Clothing>>() {}.getType());
        this.hair = net.mca.cobalt.network.PacketCodecs.json(buffer, new com.google.gson.reflect.TypeToken<HashMap<String, Hair>>() {}.getType());
    }

    public static SkinListResponse decode(net.minecraft.network.PacketByteBuf buffer) { return new SkinListResponse(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        net.mca.cobalt.network.PacketCodecs.json(buffer, this.clothing, new com.google.gson.reflect.TypeToken<HashMap<String, Clothing>>() {}.getType());
        net.mca.cobalt.network.PacketCodecs.json(buffer, this.hair, new com.google.gson.reflect.TypeToken<HashMap<String, Hair>>() {}.getType());
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
