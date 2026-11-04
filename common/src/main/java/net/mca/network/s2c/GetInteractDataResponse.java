package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.cobalt.network.Message;
import net.mca.entity.ai.relationship.RelationshipState;
import net.mca.entity.interaction.Constraint;

import java.util.Set;

public class GetInteractDataResponse implements Message {

    public final Set<Constraint> constraints;
    public final String father;
    public final String mother;
    public final String spouse;
    public final RelationshipState marriageState;

    public GetInteractDataResponse(Set<Constraint> constraints, String father, String mother, String spouse, RelationshipState marriageState) {
        this.constraints = constraints;
        this.father = father;
        this.mother = mother;
        this.spouse = spouse;
        this.marriageState = marriageState;
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleInteractDataResponse(this);
    }

    private GetInteractDataResponse(net.minecraft.network.PacketByteBuf buffer) {
        this.constraints = net.mca.cobalt.network.PacketCodecs.json(buffer, new com.google.gson.reflect.TypeToken<Set<Constraint>>() {}.getType());
        this.father = net.mca.cobalt.network.PacketCodecs.string(buffer);
        this.mother = net.mca.cobalt.network.PacketCodecs.string(buffer);
        this.spouse = net.mca.cobalt.network.PacketCodecs.string(buffer);
        this.marriageState = net.mca.cobalt.network.PacketCodecs.enumeration(buffer, RelationshipState.class);
    }

    public static GetInteractDataResponse decode(net.minecraft.network.PacketByteBuf buffer) { return new GetInteractDataResponse(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        net.mca.cobalt.network.PacketCodecs.json(buffer, this.constraints, new com.google.gson.reflect.TypeToken<Set<Constraint>>() {}.getType());
        net.mca.cobalt.network.PacketCodecs.string(buffer, this.father);
        net.mca.cobalt.network.PacketCodecs.string(buffer, this.mother);
        net.mca.cobalt.network.PacketCodecs.string(buffer, this.spouse);
        buffer.writeVarInt(this.marriageState.ordinal());
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
