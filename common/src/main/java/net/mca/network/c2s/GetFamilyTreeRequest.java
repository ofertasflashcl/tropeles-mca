package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.s2c.GetFamilyTreeResponse;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GetFamilyTreeRequest implements Message {

    final UUID uuid;

    public GetFamilyTreeRequest(UUID uuid) {
        this.uuid = uuid;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        FamilyTree.get(player.getServerWorld()).getOrEmpty(uuid).ifPresent(entry -> {
            Map<UUID, FamilyTreeNode> familyEntries = Stream.concat(
                            entry.lookup(Stream.of(entry.id(), entry.partner())),
                            entry.lookup(entry.getRelatives(2, 1))
                    ).distinct()
                    .collect(Collectors.toMap(FamilyTreeNode::id, Function.identity()));

            NetworkHandler.sendToPlayer(new GetFamilyTreeResponse(uuid, familyEntries), player);
        });
    }

    private GetFamilyTreeRequest(net.minecraft.network.PacketByteBuf buffer) {
        this.uuid = net.mca.cobalt.network.PacketCodecs.uuid(buffer);
        if (this.uuid == null) throw new io.netty.handler.codec.DecoderException("Missing uuid");
    }

    public static GetFamilyTreeRequest decode(net.minecraft.network.PacketByteBuf buffer) { return new GetFamilyTreeRequest(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        net.mca.cobalt.network.PacketCodecs.uuid(buffer, this.uuid);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
