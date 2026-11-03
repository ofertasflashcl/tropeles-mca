package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.network.NbtDataMessage;
import net.mca.resources.BuildingTypes;
import net.mca.resources.Rank;
import net.mca.resources.Tasks;
import net.mca.resources.data.BuildingType;
import net.mca.resources.data.tasks.Task;
import net.mca.server.world.data.Village;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class GetVillageResponse extends NbtDataMessage {

    public final Rank rank;
    public final int reputation;
    public final boolean isVillage;
    public final Set<String> ids;
    public final Map<Rank, List<Task>> tasks;
    public final Map<String, BuildingType> buildingTypes;

    public GetVillageResponse(Village data, Rank rank, int reputation, boolean isVillage, Set<String> ids) {
        super(data.save());
        this.rank = rank;
        this.reputation = reputation;
        this.isVillage = isVillage;
        this.ids = ids;
        this.tasks = Tasks.getInstance().tasks;
        this.buildingTypes = BuildingTypes.getInstance().getServerBuildingTypes();
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleVillageDataResponse(this);
    }

    private GetVillageResponse(net.minecraft.network.PacketByteBuf buffer) {
        super(net.mca.cobalt.network.PacketCodecs.nbt(buffer));
        this.rank = net.mca.cobalt.network.PacketCodecs.enumeration(buffer, Rank.class);
        this.reputation = buffer.readInt();
        this.isVillage = buffer.readBoolean();
        this.ids = net.mca.cobalt.network.PacketCodecs.json(buffer, new com.google.gson.reflect.TypeToken<Set<String>>() {}.getType());
        this.tasks = net.mca.cobalt.network.PacketCodecs.json(buffer, new com.google.gson.reflect.TypeToken<Map<Rank, List<Task>>>() {}.getType());
        this.buildingTypes = net.mca.cobalt.network.PacketCodecs.json(buffer, new com.google.gson.reflect.TypeToken<Map<String, BuildingType>>() {}.getType());
    }

    public static GetVillageResponse decode(net.minecraft.network.PacketByteBuf buffer) { return new GetVillageResponse(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        buffer.writeNbt(getData());
        buffer.writeVarInt(this.rank.ordinal());
        buffer.writeInt(this.reputation);
        buffer.writeBoolean(this.isVillage);
        net.mca.cobalt.network.PacketCodecs.json(buffer, this.ids, new com.google.gson.reflect.TypeToken<Set<String>>() {}.getType());
        net.mca.cobalt.network.PacketCodecs.json(buffer, this.tasks, new com.google.gson.reflect.TypeToken<Map<Rank, List<Task>>>() {}.getType());
        net.mca.cobalt.network.PacketCodecs.json(buffer, this.buildingTypes, new com.google.gson.reflect.TypeToken<Map<String, BuildingType>>() {}.getType());
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
