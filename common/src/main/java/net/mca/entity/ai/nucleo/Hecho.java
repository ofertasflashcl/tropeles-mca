package net.mca.entity.ai.nucleo;

import net.minecraft.nbt.NbtCompound;

import java.util.ArrayList;
import java.util.List;

/**
 * Something it has come to believe from its episodes, with the evidence for and against it: "Benja es buena gente"
 * ({@code trato:<uuid>}), "por 120 64 -30 hay monstruos" ({@code peligro:<celda>}), "Benja me pega cuando paso por la
 * mina" ({@code patron:...}). Belief is (for + 1) / (for + against + 2); a belief no eye ever saw and fewer than three
 * tellers back is a rumour, which may colour words but never settle anything that cannot be undone. Places fade: half
 * the weight every three days unseen.
 */
final class Hecho {
    static final long MEDIA_VIDA_LUGAR = 72000;

    final String clave;
    String valor;
    float pos;
    float neg;
    int directos;
    long creado;
    long visto;
    /** The summary episode that tells this pattern (0: none). */
    int resumen;
    final List<Integer> fuentes = new ArrayList<>(8);

    Hecho(String clave, String valor, long now) {
        this.clave = clave;
        this.valor = valor;
        this.creado = now;
        this.visto = now;
    }

    float creencia() {
        return (pos + 1.0F) / (pos + neg + 2.0F);
    }

    float soporte() {
        return pos + neg;
    }

    boolean rumor() {
        return directos == 0 && soporte() < 3.0F;
    }

    /** How much it still counts: a place unseen for long fades, a belief about someone does not. */
    float peso(long now) {
        return clave.startsWith("peligro:") ? (float) Math.pow(0.5, Math.max(0, now - visto) / (double) MEDIA_VIDA_LUGAR) : 1.0F;
    }

    void apoyar(boolean a, float cuanto, boolean directo, int episodio, long now) {
        if (a) {
            pos += cuanto;
        } else {
            neg += cuanto;
        }
        if (directo) {
            directos++;
        }
        visto = now;
        if (episodio > 0 && !fuentes.contains(episodio)) {
            fuentes.add(episodio);
            if (fuentes.size() > 8) {
                fuentes.remove(0);
            }
        }
    }

    NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putString("k", clave);
        n.putString("v", valor);
        n.putFloat("p", pos);
        n.putFloat("n", neg);
        n.putInt("d", directos);
        n.putLong("c", creado);
        n.putLong("t", visto);
        n.putIntArray("f", fuentes.stream().mapToInt(Integer::intValue).toArray());
        if (resumen != 0) {
            n.putInt("rs", resumen);
        }
        return n;
    }

    static Hecho fromNbt(NbtCompound n) {
        Hecho h = new Hecho(n.getString("k"), n.getString("v"), n.getLong("c"));
        h.pos = n.getFloat("p");
        h.neg = n.getFloat("n");
        h.directos = n.getInt("d");
        h.visto = n.getLong("t");
        h.resumen = n.getInt("rs");
        for (int f : n.getIntArray("f")) {
            h.fuentes.add(f);
        }
        return h;
    }
}
