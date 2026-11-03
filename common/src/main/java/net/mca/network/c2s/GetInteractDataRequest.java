package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.relationship.CompassionateEntity;
import net.mca.entity.ai.relationship.EntityRelationship;
import net.mca.entity.ai.relationship.RelationshipState;
import net.mca.entity.interaction.Constraint;
import net.mca.network.s2c.GetInteractDataResponse;
import net.mca.server.world.data.FamilyTreeNode;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Set;
import java.util.UUID;

public class GetInteractDataRequest implements Message {

    final UUID uuid;

    public GetInteractDataRequest(UUID villager) {
        this.uuid = villager;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        Entity entity = player.getServerWorld().getEntity(uuid);

        if (entity instanceof VillagerLike<?> villager) {
            //get constraints
            Set<Constraint> constraints = Constraint.allMatching(villager, player);

            EntityRelationship relationship = ((CompassionateEntity<?>)villager).getRelationships();
            FamilyTreeNode family = relationship.getFamilyEntry();

            String fatherName = relationship.getFamilyTree().getOrEmpty(family.father()).map(FamilyTreeNode::getName).orElse(null);
            String motherName = relationship.getFamilyTree().getOrEmpty(family.mother()).map(FamilyTreeNode::getName).orElse(null);
            String spouseName = relationship.getFamilyTree().getOrEmpty(family.partner()).map(FamilyTreeNode::getName).orElse(null);
            RelationshipState marriageState = relationship.getRelationshipState();

            NetworkHandler.sendToPlayer(new GetInteractDataResponse(constraints, fatherName, motherName, spouseName, marriageState), player);
        }
    }

    private GetInteractDataRequest(net.minecraft.network.PacketByteBuf buffer) {
        this.uuid = net.mca.cobalt.network.PacketCodecs.uuid(buffer);
        if (this.uuid == null) throw new io.netty.handler.codec.DecoderException("Missing uuid");
    }

    public static GetInteractDataRequest decode(net.minecraft.network.PacketByteBuf buffer) { return new GetInteractDataRequest(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        net.mca.cobalt.network.PacketCodecs.uuid(buffer, this.uuid);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
