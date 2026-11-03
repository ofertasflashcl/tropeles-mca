package net.mca.entity.ai.nucleo;

import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Memories;
import net.mca.entity.ai.nucleo.Episodio.Modo;
import net.mca.entity.ai.nucleo.Episodio.Tipo;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * What a villager has with one person (V21 in Sol's report on autonomous life), read each time from what already
 * keeps it, so there is no second ledger to drift away from the first:
 * <ul>
 * <li>{@code carino}, -1..1: MCA's hearts with them;</li>
 * <li>{@code confianza}, 0..1: what it came to believe of how they treat it ({@link Hecho} {@code trato:}, 0.5 when it
 * knows nothing), on {@code pruebas} worth of evidence;</li>
 * <li>{@code familiaridad}, 0..1: the time spent together and the incidents it shares with them;</li>
 * <li>{@code conflicto}, 0..1: the harm they did it lately (a hit, a theft, someone it loved hurt or killed), each
 * fading with time (a hit in about a day, a theft in three, a death in a week), and less for every gift and apology
 * that came after it (each apology worth less than the one before).</li>
 * </ul>
 * Being told what to do is none of these: an order makes no friend. Server thread; never throws.
 */
public record Vinculo(float carino, float confianza, float pruebas, float familiaridad, float conflicto, @Nullable Episodio agravio,
                      int disculpas, boolean conocido) {
    public static final Vinculo NADA = new Vinculo(0.0F, 0.5F, 0.0F, 0.0F, 0.0F, null, 0, false);

    /** From here on it will do nothing for them. */
    public static final float NO_TODO = 0.6F;
    /** From here on it does the little things grumbling and refuses what costs it. */
    public static final float NO_CARO = 0.35F;

    /** What {@code v} has with the person {@code persona} (a player or a villager) right now. */
    public static Vinculo de(ServerWorld world, VillagerEntityMCA v, UUID persona) {
        try {
            return de(v, Nucleo.de(world, v), persona, world.getTime());
        } catch (RuntimeException e) {
            return NADA;
        }
    }

    static Vinculo de(VillagerEntityMCA v, Mente m, UUID persona, long now) {
        Memories mem = v.getVillagerBrain().getMemories().get(persona);
        float carino = mem == null ? 0.0F : Mente.clamp(mem.getHearts() / 100.0F, -1.0F, 1.0F);
        long juntos = mem == null ? 0 : mem.getJuntos();
        int compartidos = 0;
        int perdones = 0;
        int regalos = 0;
        float conflicto = 0.0F;
        Episodio peor = null;
        float peorPeso = 0.0F;
        int disculpas = 0;
        // newest first: the gifts and apologies already passed came after each older harm, and make up for it
        for (int i = m.episodios.size() - 1, n = 0; i >= 0 && n < 200; i--, n++) {
            Episodio e = m.episodios.get(i);
            if (!e.vivo() || e.quien == null || !e.quien.es(persona)) {
                continue;
            }
            compartidos++;
            long dt = Math.max(0, now - e.t1);
            if (e.tipo == Tipo.PERDON) {
                perdones++;
                continue;
            }
            if (e.tipo == Tipo.REGALO || e.tipo == Tipo.CONSOLE) {
                regalos++;
                continue;
            }
            double tau;
            float peso;
            if (e.tipo == Tipo.HERIDO && e.aQuien == null) {
                tau = 24000.0;
                peso = 1.0F;
            } else if (e.tipo == Tipo.ROBO && e.aQuien == null) {
                tau = 72000.0;
                peso = 1.0F;
            } else if (e.tipo == Tipo.MUERTE) {
                tau = 168000.0;
                peso = 1.0F;
            } else if (e.tipo == Tipo.VIO_HERIR) {
                tau = 24000.0;
                peso = 0.5F;
            } else {
                continue;
            }
            if (dt > tau * 4) {
                continue;
            }
            float w = Math.abs(e.valencia) * e.confianza * peso * (e.modo == Modo.OIDO ? 0.4F : 1.0F)
                    * Math.min(2.0F, 1.0F + 0.5F * (e.veces - 1)) * (float) Math.exp(-dt / tau);
            w *= (float) Math.pow(0.5, Math.min(regalos, 3)) * perdon(perdones);
            conflicto += w;
            if (w > peorPeso) {
                peorPeso = w;
                peor = e;
                disculpas = perdones;
            }
        }
        float familiaridad = Mente.clamp((float) (1.0 - Math.exp(-(juntos / 24000.0 + compartidos / 8.0))), 0.0F, 1.0F);
        Hecho trato = m.hechos.get("trato:" + persona);
        return new Vinculo(carino, m.trato(persona), trato == null ? 0.0F : trato.soporte(), familiaridad, Mente.clamp(conflicto, 0.0F, 1.0F),
                peor, disculpas, mem != null || compartidos > 0);
    }

    /** What the apologies that came after a harm leave of it: the first takes 40%, the next ones less and less. */
    private static float perdon(int n) {
        return n <= 0 ? 1.0F : n == 1 ? 0.6F : n == 2 ? 0.48F : 0.43F;
    }

    /** "me pegaste hace un rato", said to them: the harm that weighs most now, or "" with none. */
    public String agravioATi(boolean es) {
        if (agravio == null) {
            return "";
        }
        return agravio.narrarATi(es);
    }

    /** One line for a language model: how it stands with them and why. */
    public String paraModelo(String nombre) {
        StringBuilder sb = new StringBuilder("With ").append(nombre).append(": ");
        sb.append(carino > 0.5F ? "you love them" : carino > 0.2F ? "you like them" : carino < -0.3F ? "you dislike them" : "no strong feelings");
        sb.append(", trust ").append(Math.round(confianza * 100)).append("%");
        sb.append(familiaridad > 0.6F ? ", you know them well" : familiaridad < 0.15F ? ", you barely know them" : "");
        if (conflicto >= NO_TODO) {
            sb.append("; you are angry with them (").append(agravioATi(false)).append(") and will do nothing for them until they make up for it");
        } else if (conflicto >= NO_CARO) {
            sb.append("; you are still sore (").append(agravioATi(false)).append("): small favours grumbling, nothing that costs you");
        }
        return sb.append('.').toString();
    }
}
