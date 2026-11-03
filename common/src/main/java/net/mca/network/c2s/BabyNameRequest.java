package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.ai.relationship.Gender;
import net.mca.network.s2c.BabyNameResponse;
import net.mca.resources.Names;
import net.minecraft.server.network.ServerPlayerEntity;


public class BabyNameRequest implements Message {

    private final Gender gender;

    public BabyNameRequest(Gender gender) {
        this.gender = gender;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        String name = Names.pickCitizenName(gender);
        NetworkHandler.sendToPlayer(new BabyNameResponse(name), player);
    }

    private BabyNameRequest(net.minecraft.network.PacketByteBuf buffer) {
        this.gender = net.mca.cobalt.network.PacketCodecs.enumeration(buffer, Gender.class);
        if (this.gender == null) throw new io.netty.handler.codec.DecoderException("Missing gender");
    }

    public static BabyNameRequest decode(net.minecraft.network.PacketByteBuf buffer) { return new BabyNameRequest(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        buffer.writeVarInt(this.gender.ordinal());
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
