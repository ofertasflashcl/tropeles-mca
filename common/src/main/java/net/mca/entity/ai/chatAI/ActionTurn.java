package net.mca.entity.ai.chatAI;

import net.mca.MCA;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.mente.Voluntad;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** A chat request's lease. World/entity reads and effect claims belong to the server thread. */
public final class ActionTurn {
    private final MinecraftServer server;
    private final ServerWorld world;
    private final ServerWorld playerWorld;
    private final ServerPlayerEntity player;
    private final VillagerEntityMCA villager;
    private final UUID villagerId;
    private final UUID playerId;
    private final long expires;
    private final AtomicBoolean finished = new AtomicBoolean();
    private final AtomicBoolean acted = new AtomicBoolean();
    private volatile int generation;

    private ActionTurn(ServerPlayerEntity player, VillagerEntityMCA villager, long remainingMs) {
        this.server = player.getServer();
        this.player = player;
        this.villager = villager;
        this.world = (ServerWorld) villager.getWorld();
        this.playerWorld = player.getServerWorld();
        this.villagerId = villager.getUuid();
        this.playerId = player.getUuid();
        this.generation = Voluntad.generacion(villagerId);
        this.expires = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0, remainingMs));
    }

    public static ActionTurn capture(ServerPlayerEntity player, VillagerEntityMCA villager, long remainingMs) {
        if (player.getServer() == null || !player.getServer().isOnThread()) {
            throw new IllegalStateException("ActionTurn must be captured on the server thread");
        }
        return new ActionTurn(player, villager, remainingMs);
    }

    /** Validate again inside the submitted server task, never just before submitting it. */
    public boolean valid() {
        if (finished.get()) return false;
        if (!server.isOnThread()) throw new IllegalStateException("ActionTurn validation off server thread");
        String why = null;
        if (!server.isRunning() || ServerRuntime.closed(server)) why = "servidor cerrado";
        else if (System.nanoTime() - expires >= 0) why = "plazo agotado";
        else if (villager.getWorld() != world || server.getWorld(world.getRegistryKey()) != world
                || player.getServerWorld() != playerWorld) why = "mundo o dimensión cambió";
        else if (!villager.getUuid().equals(villagerId) || villager.isRemoved() || !villager.isAlive() || world.getEntity(villagerId) != villager) why = "aldeano ausente o sustituido";
        else if (!player.getUuid().equals(playerId) || player.isRemoved() || !player.isAlive() || player.getServer() != server
                || player.networkHandler != null && server.getPlayerManager().getPlayer(player.getUuid()) != player) why = "jugador desconectado";
        else if (Voluntad.generacion(villagerId) != generation) why = "intención cambió";
        if (why != null) cancel(why);
        return why == null;
    }

    /** Exactly one physical command may be attempted by this request, including local/model retries. */
    public boolean claimAction() {
        return valid() && acted.compareAndSet(false, true);
    }

    /** Its own synchronous command may change generation; an unrelated later change must still invalidate it. */
    public void acknowledgeAction() {
        if (!server.isOnThread() || !acted.get()) throw new IllegalStateException("No server action to acknowledge");
        generation = Voluntad.generacion(villagerId);
    }

    public boolean complete() {
        return valid() && finished.compareAndSet(false, true);
    }

    public boolean finished() {
        return finished.get();
    }

    public void cancel(String why) {
        if (finished.compareAndSet(false, true)) {
            MCA.LOGGER.info("[Tropeles] resultado VENCIDO charla {}: {}; no dice ni ejecuta la respuesta", villagerId, why);
        }
    }
}
