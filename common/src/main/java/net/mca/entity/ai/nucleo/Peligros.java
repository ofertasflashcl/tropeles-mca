package net.mca.entity.ai.nucleo;

import net.mca.MCA;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.body.Mining;
import net.mca.entity.ai.nucleo.Episodio.Modo;
import net.mca.entity.ai.nucleo.Episodio.Tipo;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Fear that belongs to places (V20 in Sol's report on autonomous life). What it lived somewhere (hit, fled, saw
 * someone hurt, fought, always by a monster) makes it believe at once that the place is dangerous: a belief
 * {@code peligro:} for that cell of 16 blocks in that dimension ({@link Hecho}), not only after the next night's
 * sleep. The belief changes what it does: where it starts a mine, which way it explores, which animal it hunts, and it
 * warns whoever it walks with. Staying there again unharmed is evidence against it, so the fear is learnt and
 * unlearnt by what happens, and a fear that nothing feeds fades with the days. Server thread; never throws.
 */
public final class Peligros {
    /** From here on the fear decides (no mine started here); below it only weighs (it prefers another way). */
    public static final float FUERTE = 0.5F;
    /** Every this many ticks unharmed in a feared cell, with no monster close, is one piece of evidence against it. */
    private static final int SEGURO_CADA = 600;
    private static final float SEGURO_PESO = 0.25F;
    /** How fast what happened there stops being fresh (e-fold, ticks). */
    private static final double FRESCO = 24000.0;

    /** What it fears of a place: 0 nothing .. 1 it will not go; what it remembers of it, in its own words. */
    public record Miedo(float cuanto, String es, String en) {
        public static final Miedo NADA = new Miedo(0.0F, "", "");

        public boolean fuerte() {
            return cuanto >= FUERTE;
        }
    }

    private Peligros() {
    }

    /** The belief's key: the same cell in another dimension is another place. */
    static String clave(String dim, BlockPos p) {
        return "peligro:" + (dim.isEmpty() ? "" : dim + "|") + (p.getX() >> 4) + "," + (p.getY() >> 4) + "," + (p.getZ() >> 4);
    }

    static String dim(World world) {
        return world.getRegistryKey().getValue().toString();
    }

    /** An incident that tells of danger in a place: something a monster did there. */
    static boolean dePeligro(Episodio e) {
        return e.quien != null && e.quien.clase() == Episodio.BICHO && e.donde != null
                && (e.tipo == Tipo.HERIDO || e.tipo == Tipo.HUI || e.tipo == Tipo.VIO_HERIR || e.tipo == Tipo.PELEA);
    }

    /** A new incident of danger is evidence for its place right away; each incident counts once (sleep skips it). */
    static void anotar(Mente m, Episodio e, long now) {
        if (!dePeligro(e)) {
            return;
        }
        Hecho h = m.hecho(clave(e.dim, e.donde), e.quien.es() + " cerca de " + e.dondeTexto(true), now);
        if (!h.fuentes.contains(e.id)) {
            h.apoyar(true, e.confianza, e.modo == Modo.VISTO, e.id, now);
        }
    }

    /** What {@code v} fears of the place {@code at} in its world, from what it lived (or heard) there. */
    public static Miedo de(ServerWorld world, VillagerEntityMCA v, BlockPos at) {
        try {
            return de(Nucleo.de(world, v), dim(world), at, world.getTime());
        } catch (RuntimeException ex) {
            return Miedo.NADA;
        }
    }

    static Miedo de(Mente m, String dim, BlockPos at, long now) {
        Hecho h = m.hechos.get(clave(dim, at));
        if (h == null && "minecraft:overworld".equals(dim)) {
            h = m.hechos.get(clave("", at)); // kept before the dimension was
        }
        if (h == null || h.pos <= 0.0F) {
            return Miedo.NADA;
        }
        // what happened against how often it was fine there since; fresher hurts more; a rumour half
        float base = h.pos / (h.pos + h.neg + 0.5F);
        Episodio ultimo = ultimo(m, h);
        long t = ultimo == null ? h.creado : ultimo.t1;
        float fresco = (float) Math.exp(-Math.max(0, now - t) / FRESCO);
        float cuanto = base * (0.35F + 0.65F * fresco) * h.peso(now) * (h.rumor() ? 0.5F : 1.0F);
        cuanto = MathHelper.clamp(cuanto, 0.0F, 1.0F);
        if (cuanto < 0.05F) {
            return Miedo.NADA;
        }
        String[] dicho = ultimo == null ? new String[]{"por aquí hay peligro (" + h.valor + ")", "it's dangerous around here"}
                : contar(ultimo, now);
        return new Miedo(cuanto, dicho[0], dicho[1]);
    }

    /** The newest incident still in the journal behind this belief. */
    @Nullable
    private static Episodio ultimo(Mente m, Hecho h) {
        Episodio best = null;
        for (int id : h.fuentes) {
            Episodio e = m.porId(id);
            if (e != null && e.vivo() && (best == null || e.t1 > best.t1)) {
                best = e;
            }
        }
        return best;
    }

    /** "aquí me atacó un zombi hace un rato", told the way it knows it. */
    private static String[] contar(Episodio e, long now) {
        String quien = e.quien == null ? "algo" : e.quien.es();
        String who = e.quien == null ? "something" : e.quien.en();
        long dt = Math.max(0, now - e.t1);
        String cuando = dt < 1200 ? "hace un rato" : dt < 12000 ? "hoy" : dt < 36000 ? "ayer" : "hace unos días";
        String when = dt < 1200 ? "a moment ago" : dt < 12000 ? "today" : dt < 36000 ? "yesterday" : "a few days ago";
        if (e.modo == Modo.OIDO) {
            return new String[]{"me contaron que por aquí anda " + quien, "I was told " + who + " hangs around here"};
        }
        return switch (e.tipo) {
            case HUI -> new String[]{"aquí tuve que huir de " + quien + " " + cuando, "I had to run from " + who + " here " + when};
            case VIO_HERIR -> new String[]{"aquí vi a " + quien + " atacar" + (e.aQuien == null ? "" : " a " + e.aQuien.es()) + " " + cuando,
                    "I saw " + who + " attack" + (e.aQuien == null ? "" : " " + e.aQuien.en()) + " here " + when};
            case PELEA -> new String[]{"aquí peleé con " + quien + " " + cuando, "I fought " + who + " here " + when};
            default -> new String[]{"aquí me atacó " + quien + " " + cuando, who + " attacked me here " + when};
        };
    }

    /** The worst fear along a heading, at a few points from {@code near} to {@code far} blocks. */
    public static float rumbo(ServerWorld world, VillagerEntityMCA v, BlockPos feet, float yaw, int near, int far) {
        try {
            Mente m = Nucleo.de(world, v);
            if (m.hechos.isEmpty()) {
                return 0.0F;
            }
            String dim = dim(world);
            long now = world.getTime();
            double sx = -MathHelper.sin(yaw * MathHelper.RADIANS_PER_DEGREE);
            double sz = MathHelper.cos(yaw * MathHelper.RADIANS_PER_DEGREE);
            float worst = 0.0F;
            for (int d = near; d <= far; d += 16) {
                BlockPos p = feet.add((int) Math.round(sx * d), 0, (int) Math.round(sz * d));
                worst = Math.max(worst, de(m, dim, p, now).cuanto());
            }
            return worst;
        } catch (RuntimeException ex) {
            return 0.0F;
        }
    }

    /**
     * Once a second from {@link Nucleo#tick}: every {@value #SEGURO_CADA} ticks spent unharmed in a cell it fears,
     * with no monster close in that time, is a piece of evidence that the place is fine now.
     */
    static void seguro(ServerWorld world, VillagerEntityMCA v, Mente m, long now) {
        if ((now / 20 + v.getId()) % (SEGURO_CADA / 20) != 0 || m.hechos.isEmpty()) {
            return;
        }
        BlockPos feet = Mining.pies(v);
        String dim = dim(world);
        Hecho h = m.hechos.get(clave(dim, feet));
        if (h == null && "minecraft:overworld".equals(dim)) {
            h = m.hechos.get(clave("", feet));
        }
        if (h == null || h.pos <= 0.0F || h.creencia() <= 0.5F) {
            return;
        }
        if (now < m.dolorHasta || now - m.amenazaT < SEGURO_CADA || v.hurtTime > 0 || v.getVillagerBrain().isPanicking()) {
            return;
        }
        h.apoyar(false, SEGURO_PESO, true, 0, now);
        m.sucio(now);
        MCA.LOGGER.info("[Tropeles] {} sigue bien en {} ({}): le pierde el miedo a ese lugar (miedo {})", v.getName().getString(),
                feet.toShortString(), h.valor, String.format(java.util.Locale.ROOT, "%.2f", de(m, dim, feet, now).cuanto()));
    }
}
