package net.mca.entity.ai.obra;

import java.text.Normalizer;
import java.util.Locale;

/** Deterministic selection for a new project; an active work keeps its frozen blueprint. */
public final class PedidoCasa {
    private PedidoCasa() {
    }

    public static Plano elegir(String palabras, Plano.Madera madera) {
        String s = Normalizer.normalize(palabras == null ? "" : palabras, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        String id = s.matches(".*\\b(cabana|cabin|hut)\\b.*") ? "tropeles:casa/cabana"
                : s.matches(".*\\b(casa (larga|alargada)|long house|longhouse)\\b.*") ? "tropeles:casa/larga"
                : "tropeles:legacy/casa_dos_pisos";
        Plano p = CatalogoPlanos.plano(id, madera);
        if (p == null) {
            throw new IllegalStateException("Missing built-in house: " + id);
        }
        return p;
    }
}
