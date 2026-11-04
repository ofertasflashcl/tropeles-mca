package net.mca.network.c2s;

import net.mca.Config;
import net.mca.MCA;
import net.mca.cobalt.network.Message;
import net.mca.util.WorldUtils;
import net.mca.util.compat.ExtendedFuzzyPositions;
import net.minecraft.entity.ai.FuzzyPositions;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;

import java.util.EnumSet;

public class DestinyMessage implements Message {

    private final String location;
    private final boolean isClosing;

    public DestinyMessage(String location, boolean isClosing) {
        this.location = location;
        this.isClosing = isClosing;
    }

    public DestinyMessage(String location) {
        this(location, false);
    }

    public DestinyMessage(boolean isClosing) {
        this(null, isClosing);
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        if (isClosing) {
            player.removeStatusEffect(StatusEffects.INVISIBILITY);
            player.removeStatusEffect(StatusEffects.HEALTH_BOOST);
        }

        if (Config.getInstance().allowDestinyTeleportation && location != null) {
            MCA.executorService.execute(() -> {
                if (location.charAt(0) == '#') {
                    String tagId = location.substring(1);
                    WorldUtils.getClosestStructurePosition(player.getServerWorld(), player.getBlockPos(), TagKey.of(RegistryKeys.STRUCTURE, new Identifier(tagId)), 128).ifPresent(pos -> handleBlockPos(player, pos));
                } else {
                    WorldUtils.getClosestStructurePosition(player.getServerWorld(), player.getBlockPos(), new Identifier(location), 128).ifPresent(pos -> handleBlockPos(player, pos));
                }
            });
        }
    }


    private void handleBlockPos(ServerPlayerEntity player, BlockPos pos) {
        player.getWorld().getWorldChunk(pos);

        if (location.equals("minecraft:ancient_city")) {
            pos = new BlockPos(pos.getX(), -50, pos.getZ());
        } else {
            pos = player.getWorld().getTopPosition(Heightmap.Type.WORLD_SURFACE, pos);
        }

        pos = FuzzyPositions.upWhile(pos, player.getWorld().getHeight(), p -> player.getWorld().getBlockState(p).shouldSuffocate(player.getWorld(), p));
        pos = ExtendedFuzzyPositions.downWhile(pos, 1, p -> !player.getWorld().getBlockState(p.down()).isFullCube(player.getWorld(), p));

        ChunkPos chunkPos = new ChunkPos(pos);
        player.getServerWorld().getChunkManager().addTicket(ChunkTicketType.POST_TELEPORT, chunkPos, 1, player.getId());
        player.networkHandler.requestTeleport(pos.getX(), pos.getY(), pos.getZ(), player.getYaw(), player.getPitch(), EnumSet.noneOf(PositionFlag.class));

        //set spawn
        player.setSpawnPoint(player.getWorld().getRegistryKey(), pos, 0.0f, true, false);
        if (player.getWorld().getServer() != null && player.getWorld().getServer().isHost(player.getGameProfile())) {
            player.getServerWorld().setSpawnPos(pos, 0.0f);
        }
    }

    private DestinyMessage(net.minecraft.network.PacketByteBuf buffer) {
        this.location = net.mca.cobalt.network.PacketCodecs.string(buffer);
        this.isClosing = buffer.readBoolean();
        // closing the screen (DestinyMessage(true)) carries no location; only a chosen destiny needs one
        if (this.location == null && !this.isClosing) throw new io.netty.handler.codec.DecoderException("Missing location");
    }

    public static DestinyMessage decode(net.minecraft.network.PacketByteBuf buffer) { return new DestinyMessage(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        net.mca.cobalt.network.PacketCodecs.string(buffer, this.location);
        buffer.writeBoolean(this.isClosing);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
