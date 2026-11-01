package net.mca.entity.ai.obra;

import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.s2c.FantasmaMessage;
import net.minecraft.block.Block;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The plan of every building under way, sent to the players near it so their game draws it as a see-through ghost
 * (the "The Forest" way): sent once when they come within {@value #RADIO} blocks, again when it moves, taken away
 * when they leave, when it is done or given up. Server thread only; never throws into the server tick.
 */
public final class Proyeccion {
    private static final double RADIO = 160.0;
    private static final int CADA = 40;
    private static final int REENVIO = 6000;

    /** What each player was sent: building id -> {plan version, when}, and in which world. */
    private static final Map<UUID, Map<String, long[]>> ENVIADO = new HashMap<>();
    private static final Map<UUID, String> MUNDO = new HashMap<>();

    private Proyeccion() {
    }

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % CADA != 0) {
            return;
        }
        for (ServerWorld world : server.getWorlds()) {
            List<ServerPlayerEntity> players = world.getPlayers();
            if (players.isEmpty()) {
                continue;
            }
            ObrasMundo obras = ObrasMundo.get(world);
            String dim = dim(world);
            long now = world.getTime();
            for (ServerPlayerEntity p : players) {
                if (!dim.equals(MUNDO.put(p.getUuid(), dim))) {
                    ENVIADO.remove(p.getUuid()); // another world: its ghosts there are drawn only there
                }
                Map<String, long[]> sent = ENVIADO.computeIfAbsent(p.getUuid(), k -> new HashMap<>());
                for (Iterator<Map.Entry<String, long[]>> it = sent.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<String, long[]> e = it.next();
                    Obra o = obras.porId(e.getKey());
                    if (o == null || !o.centro().isWithinDistance(p.getPos(), RADIO + 16)) {
                        NetworkHandler.sendToPlayer(FantasmaMessage.borrar(e.getKey(), dim), p);
                        it.remove();
                    }
                }
                for (Obra o : obras.todas()) {
                    if (!o.centro().isWithinDistance(p.getPos(), RADIO)) {
                        continue;
                    }
                    long[] s = sent.get(o.id);
                    if (s == null || s[0] != o.version || now - s[1] > REENVIO) {
                        NetworkHandler.sendToPlayer(mensaje(world, o), p);
                        sent.put(o.id, new long[]{o.version, now});
                    }
                }
            }
        }
    }

    /** Right away to the players near it (a new plan, or moved). */
    public static void enviar(ServerWorld world, Obra o) {
        try {
            FantasmaMessage m = mensaje(world, o);
            for (ServerPlayerEntity p : world.getPlayers()) {
                if (o.centro().isWithinDistance(p.getPos(), RADIO)) {
                    NetworkHandler.sendToPlayer(m, p);
                    ENVIADO.computeIfAbsent(p.getUuid(), k -> new HashMap<>()).put(o.id, new long[]{o.version, world.getTime()});
                    MUNDO.put(p.getUuid(), dim(world));
                }
            }
        } catch (RuntimeException e) {
            net.mca.MCA.LOGGER.warn("[Tropeles] no se pudo enviar el plano de {}: {}", o.id, e.toString());
        }
    }

    /** The ghost goes from every game it was sent to (done, or given up). */
    public static void borrar(ServerWorld world, Obra o) {
        try {
            String dim = dim(world);
            for (ServerPlayerEntity p : world.getServer().getPlayerManager().getPlayerList()) {
                Map<String, long[]> sent = ENVIADO.get(p.getUuid());
                if (sent != null && sent.remove(o.id) != null || p.getWorld() == world) {
                    NetworkHandler.sendToPlayer(FantasmaMessage.borrar(o.id, dim), p);
                }
            }
        } catch (RuntimeException e) {
            net.mca.MCA.LOGGER.warn("[Tropeles] no se pudo borrar el plano de {}: {}", o.id, e.toString());
        }
    }

    /** A player (re)joined: their game has no ghosts yet. */
    public static void olvidarJugador(ServerPlayerEntity player) {
        ENVIADO.remove(player.getUuid());
        MUNDO.remove(player.getUuid());
    }

    private static String dim(ServerWorld world) {
        return world.getRegistryKey().getValue().toString();
    }

    private static FantasmaMessage mensaje(ServerWorld world, Obra o) {
        List<Obra.Celda> cells = o.celdas();
        int n = cells.size();
        int[] xs = new int[n];
        int[] ys = new int[n];
        int[] zs = new int[n];
        int[] estados = new int[n];
        byte[] roles = new byte[n];
        for (int i = 0; i < n; i++) {
            Obra.Celda c = cells.get(i);
            xs[i] = c.pos().getX();
            ys[i] = c.pos().getY();
            zs[i] = c.pos().getZ();
            estados[i] = Block.getRawIdFromState(c.state());
            roles[i] = (byte) c.rol().ordinal();
        }
        String quien = o.constructorNombre + (o.paraNombre.isEmpty() ? "" : " → " + o.paraNombre);
        return new FantasmaMessage(o.id, dim(world), quien, xs, ys, zs, estados, roles);
    }
}
