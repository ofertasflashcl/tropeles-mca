package net.mca.network.c2s;

import net.mca.client.gui.FamilyTreeSearchScreen;
import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.s2c.FamilyTreeUUIDResponse;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.stream.Collectors;

public class FamilyTreeUUIDLookup implements Message {

    private final String search;

    public FamilyTreeUUIDLookup(String search) {
        this.search = search;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        FamilyTree tree = FamilyTree.get(player.getServerWorld());
        List<FamilyTreeSearchScreen.Entry> list = tree.getAllWithName(search)
                .map(entry -> new FamilyTreeSearchScreen.Entry(
                        entry.id(),
                        entry.getName(),
                        tree.getOrEmpty(entry.father()).map(FamilyTreeNode::getName).orElse(""),
                        tree.getOrEmpty(entry.mother()).map(FamilyTreeNode::getName).orElse("")))
                .limit(16)
                .collect(Collectors.toList());
        NetworkHandler.sendToPlayer(new FamilyTreeUUIDResponse(list), player);
    }

    private FamilyTreeUUIDLookup(net.minecraft.network.PacketByteBuf buffer) {
        this.search = net.mca.cobalt.network.PacketCodecs.string(buffer);
        if (this.search == null) throw new io.netty.handler.codec.DecoderException("Missing search");
    }

    public static FamilyTreeUUIDLookup decode(net.minecraft.network.PacketByteBuf buffer) { return new FamilyTreeUUIDLookup(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        net.mca.cobalt.network.PacketCodecs.string(buffer, this.search);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
