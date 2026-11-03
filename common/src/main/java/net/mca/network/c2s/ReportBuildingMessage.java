package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.server.world.data.Building;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.Optional;

public class ReportBuildingMessage implements Message {

    private final Action action;
    private final String data;

    public ReportBuildingMessage(Action action, String data) {
        this.action = action;
        this.data = data;
    }

    public ReportBuildingMessage(Action action) {
        this(action, null);
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        VillageManager villages = VillageManager.get(player.getServerWorld());
        switch (action) {
            case ADD, ADD_ROOM -> {
                Building.validationResult result = villages.processBuilding(player.getBlockPos(), true, action == Action.ADD_ROOM);
                player.sendMessage(Text.translatable("blueprint.scan." + result.name().toLowerCase(Locale.ENGLISH)), true);
            }
            case AUTO_SCAN -> villages.findNearestVillage(player).ifPresent(Village::toggleAutoScan);
            case FULL_SCAN -> villages.findNearestVillage(player).ifPresent(buildings ->
                    buildings.getBuildings().values().stream().toList().forEach(b ->
                            villages.processBuilding(b.getCenter(), true, b.isStrictScan())
                    )
            );
            case FORCE_TYPE, REMOVE -> {
                Optional<Village> village = villages.findNearestVillage(player);
                Optional<Building> building = village.flatMap(v -> v.getBuildings().values().stream()
                        .filter(b -> b.containsPos(player.getBlockPos()))
                        .filter(b -> action != Action.FORCE_TYPE || !b.getBuildingType().grouped())
                        .findAny());
                building.ifPresentOrElse(b -> {
                    if (action == Action.FORCE_TYPE) {
                        if (b.getType().equals(data)) {
                            b.setTypeForced(false);
                            b.determineType();
                        } else {
                            b.setTypeForced(true);
                            b.setType(data);
                        }
                    } else {
                        //noinspection OptionalGetWithoutIsPresent
                        village.get().removeBuilding(b.getId());
                    }
                }, () -> {
                    player.sendMessage(Text.translatable("blueprint.noBuilding"), true);
                });
            }
        }
    }

    public enum Action {
        AUTO_SCAN,
        ADD_ROOM,
        ADD,
        REMOVE,
        FORCE_TYPE,
        FULL_SCAN
    }

    private ReportBuildingMessage(net.minecraft.network.PacketByteBuf buffer) {
        this.action = net.mca.cobalt.network.PacketCodecs.enumeration(buffer, Action.class);
        this.data = net.mca.cobalt.network.PacketCodecs.string(buffer);
        if (this.action == null) throw new io.netty.handler.codec.DecoderException("Missing action");
        if (this.data == null) throw new io.netty.handler.codec.DecoderException("Missing data");
    }

    public static ReportBuildingMessage decode(net.minecraft.network.PacketByteBuf buffer) { return new ReportBuildingMessage(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        buffer.writeVarInt(this.action.ordinal());
        net.mca.cobalt.network.PacketCodecs.string(buffer, this.data);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
