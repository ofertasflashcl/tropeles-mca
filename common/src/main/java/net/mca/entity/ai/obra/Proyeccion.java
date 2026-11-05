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
 * when they leave, when it is done or given up. A house only proposed ({@link Propuestas}) is drawn the same way, but
 * only for the player it is for, until they say yes or no, or it is left a day. Server thread only; never throws into
 * the server tick.
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
            long now = world.getTime();
            Propuestas propuestas = Propuestas.get(world);
            for (Propuestas.Propuesta q : propuestas.caducar(now)) {
                net.mca.MCA.LOGGER.info("[Tropeles] la propuesta de casa {} de {} para {} caducó sin respuesta", q.id, q.constructorNombre, q.paraNombre);
                borrarPropuesta(world, q);
            }
            List<ServerPlayerEntity> players = world.getPlayers();
            if (players.isEmpty()) {
                continue;
            }
            ObrasMundo obras = ObrasMundo.get(world);
            String dim = dim(world);
            for (ServerPlayerEntity p : players) {
                if (!dim.equals(MUNDO.put(p.getUuid(), dim))) {
                    ENVIADO.remove(p.getUuid()); // another world: its ghosts there are drawn only there
                }
                Map<String, long[]> sent = ENVIADO.computeIfAbsent(p.getUuid(), k -> new HashMap<>());
                for (Iterator<Map.Entry<String, long[]>> it = sent.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<String, long[]> e = it.next();
                    boolean gone;
                    if (e.getKey().startsWith(Propuestas.PREFIJO) || e.getKey().startsWith(Propuestas.PARCELA)) {
                        Propuestas.Propuesta q = propuestas.porId(e.getKey().substring(e.getKey().indexOf(':') + 1));
                        gone = q == null || !e.getKey().equals(q.llaveVista()) || !visible(p, q, RADIO + 16);
                    } else {
                        Obra o = obras.porId(e.getKey());
                        gone = o == null || !o.centro().isWithinDistance(p.getPos(), RADIO + 16);
                    }
                    if (gone) {
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
                for (Propuestas.Propuesta q : propuestas.todas()) {
                    if (!visible(p, q, RADIO)) {
                        continue;
                    }
                    String key = q.llaveVista();
                    long[] s = sent.get(key);
                    if (s == null || s[0] != q.version() || now - s[1] > REENVIO) {
                        NetworkHandler.sendToPlayer(mensaje(world, q.vista()), p);
                        sent.put(key, new long[]{q.version(), now});
                    }
                }
            }
        }
    }

    private static boolean visible(ServerPlayerEntity p, Propuestas.Propuesta q, double radio) {
        return q.para.equals(p.getUuid()) && (q.pendienteSitio() || q.vista().centro().isWithinDistance(p.getPos(), radio));
    }

    /** A proposal, right away to the one it is for (new, changed or moved): nobody else sees it. */
    public static void enviarPropuesta(ServerWorld world, Propuestas.Propuesta q) {
        try {
            ServerPlayerEntity p = world.getServer().getPlayerManager().getPlayer(q.para);
            if (p == null || p.getWorld() != world || !visible(p, q, RADIO)) {
                return;
            }
            Map<String, long[]> sent = ENVIADO.computeIfAbsent(p.getUuid(), k -> new HashMap<>());
            String anterior = (q.pendienteSitio() ? Propuestas.PREFIJO : Propuestas.PARCELA) + q.id;
            if (sent.remove(anterior) != null) {
                NetworkHandler.sendToPlayer(FantasmaMessage.borrar(anterior, dim(world)), p);
            }
            NetworkHandler.sendToPlayer(mensaje(world, q.vista()), p);
            sent.put(q.llaveVista(), new long[]{q.version(), world.getTime()});
            MUNDO.put(p.getUuid(), dim(world));
        } catch (RuntimeException e) {
            net.mca.MCA.LOGGER.warn("[Tropeles] no se pudo enviar la propuesta {}: {}", q.id, e.toString());
        }
    }

    /** A proposal's ghost goes (approved, refused, dropped or left a day). */
    public static void borrarPropuesta(ServerWorld world, Propuestas.Propuesta q) {
        try {
            String dim = dim(world);
            for (ServerPlayerEntity p : world.getServer().getPlayerManager().getPlayerList()) {
                Map<String, long[]> sent = ENVIADO.get(p.getUuid());
                for (String key : List.of(Propuestas.PREFIJO + q.id, Propuestas.PARCELA + q.id)) {
                    if (sent != null && sent.remove(key) != null || p.getUuid().equals(q.para)) {
                        NetworkHandler.sendToPlayer(FantasmaMessage.borrar(key, dim), p);
                    }
                }
            }
        } catch (RuntimeException e) {
            net.mca.MCA.LOGGER.warn("[Tropeles] no se pudo borrar la propuesta {}: {}", q.id, e.toString());
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
