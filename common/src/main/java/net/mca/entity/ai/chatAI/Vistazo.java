package net.mca.entity.ai.chatAI;

import net.mca.Config;
import net.mca.MCA;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.sense.Ahora;
import net.mca.entity.ai.sense.Entorno;
import net.mca.entity.ai.sense.Escena;
import net.mca.entity.ai.sense.Lugar;
import net.mca.entity.ai.sense.Mirada;
import net.mca.entity.ai.sense.VistaSituada;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the chat asks of the villager's eyes, always on the server thread: a fresh look around when someone talks
 * to it (so even an idle villager has just seen where it is), the scene it understands, its sense of place, and a
 * small picture of its view for the minds that can look at one. Every sense may fail (a world not ready, a sense
 * still being built): then the chat goes on with less, never without an answer.
 */
public final class Vistazo {
    /** A villager looks around again for the chat at most this often (real time). */
    private static final long LOOK_MS = 5_000;
    /** The scene is made of what it saw this many ticks back at most. */
    public static final int SCENE_TICKS = 1200;

    private static final Map<UUID, Long> LOOKED = new ConcurrentHashMap<>();

    private Vistazo() {
    }

    /** A whole look around, unless it looked around for the chat in the last few seconds. Server thread. */
    public static void fresh(VillagerEntityMCA villager) {
        if (!(villager.getWorld() instanceof ServerWorld world) || !villager.isAlive() || villager.isSleeping()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = LOOKED.get(villager.getUuid());
        if (last != null && now - last < LOOK_MS) {
            return;
        }
        LOOKED.put(villager.getUuid(), now);
        if (LOOKED.size() > 512) {
            LOOKED.values().removeIf(t -> now - t > LOOK_MS);
        }
        try {
            Mirada.lookAround(world, villager);
        } catch (RuntimeException e) {
            MCA.LOGGER.warn("[Tropeles] {} no pudo mirar a su alrededor para la charla: {}", villager.getName().getString(), e.toString());
        }
    }

    /** What it understands of its surroundings now, or null when its eyes could not tell. Server thread. */
    @Nullable
    public static Escena escena(VillagerEntityMCA villager) {
        if (!(villager.getWorld() instanceof ServerWorld world)) {
            return null;
        }
        try {
            return Escena.of(world, villager, SCENE_TICKS);
        } catch (RuntimeException e) {
            MCA.LOGGER.warn("[Tropeles] la escena de {} no se pudo armar: {}", villager.getName().getString(), e.toString());
            return null;
        }
    }

    /** The moment it understands now ({@link Ahora}: light, what it hears, structures, dangers), or null. Server thread. */
    @Nullable
    public static Ahora ahora(VillagerEntityMCA villager) {
        if (!(villager.getWorld() instanceof ServerWorld world)) {
            return null;
        }
        try {
            return Ahora.of(world, villager);
        } catch (RuntimeException e) {
            MCA.LOGGER.warn("[Tropeles] {} no pudo entender el momento: {}", villager.getName().getString(), e.toString());
            return null;
        }
    }

    /** Where it is (outside, under a roof, down in the rock), or null. */
    @Nullable
    public static Lugar lugar(VillagerEntityMCA villager) {
        if (!(villager.getWorld() instanceof ServerWorld world)) {
            return null;
        }
        try {
            return Entorno.lugar(world, villager);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Blocks below the land, or 0 when it cannot tell. */
    public static int profundidad(VillagerEntityMCA villager) {
        if (!(villager.getWorld() instanceof ServerWorld world)) {
            return 0;
        }
        try {
            return Entorno.profundidad(world, villager);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Whether a question is about seeing or the surroundings: worth a picture for the minds that can look. */
    public static boolean aboutSight(MenteLocal.Intent intent) {
        String q = intent.question();
        return !intent.order() && q != null && (q.equals("see") || q.equals("focus") || q.equals("where") || q.equals("light") || q.startsWith("see:"));
    }

    @Nullable
    public static VistaSituada captar(VillagerEntityMCA v, boolean imagen) {
        if (!(v.getWorld() instanceof ServerWorld world) || !v.isAlive() || v.isSleeping()) {
            return null;
        }
        try {
            return VistaSituada.captar(world, v, imagen && Config.getInstance().tropelesChatVision);
        } catch (RuntimeException e) {
            MCA.LOGGER.warn("[Tropeles] {} no pudo captar su vista: {}", v.getName().getString(), e.toString());
            return null;
        }
    }
}
