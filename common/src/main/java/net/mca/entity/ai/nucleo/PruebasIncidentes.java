package net.mca.entity.ai.nucleo;

import net.minecraft.util.math.BlockPos;
import java.util.UUID;

/** Contract checks on the actual episodic fold and its persisted form. */
public final class PruebasIncidentes {
    private PruebasIncidentes() { }

    private static Episodio episode(Mente m, UUID id, Episodio.Tipo type, long t, String dimension, String thing, int x) {
        Episodio e = m.nuevo(type, t, id);
        e.dim = dimension;
        e.donde = new BlockPos(x, 70, 0);
        e.cosa = thing;
        return e;
    }

    public static String run() {
        UUID a = UUID.fromString("11111111-0000-0000-0000-000000000001");
        UUID b = UUID.fromString("11111111-0000-0000-0000-000000000002");
        Mente m = new Mente();
        Episodio first = episode(m, a, Episodio.Tipo.HERIDO, 100, "minecraft:overworld", "", 1);
        m.registrar(first, 100);
        Episodio nether = episode(m, a, Episodio.Tipo.HERIDO, 101, "minecraft:the_nether", "", 1);
        m.registrar(nether, 101);
        m.registrar(episode(m, a, Episodio.Tipo.HERIDO, 102, "minecraft:overworld", "", 1), 102);
        m.registrar(episode(m, a, Episodio.Tipo.HERIDO, 103, "minecraft:overworld", "", 40), 103);
        m.registrar(episode(m, a, Episodio.Tipo.HALLAZGO, 200, "minecraft:overworld", "hierro", 1), 200);
        m.registrar(episode(m, a, Episodio.Tipo.HALLAZGO, 201, "minecraft:overworld", "diamante", 1), 201);
        if (m.episodios.size() != 5 || first.veces != 2 || nether.veces != 1 || !first.dim.equals("minecraft:overworld")) {
            throw new IllegalStateException("Se mezclaron incidentes distintos");
        }
        Mente other = new Mente();
        if (new Mente().nuevo(Episodio.Tipo.HERIDO, 1, a).origen.equals(other.nuevo(Episodio.Tipo.HERIDO, 1, b).origen)) {
            throw new IllegalStateException("Origen con UUID abreviado colisiona");
        }
        Mente restored = Mente.fromNbt(m.toNbt());
        if (restored.episodios.size() != 5 || restored.porId(first.id) == null || !restored.porId(nether.id).dim.equals("minecraft:the_nether")) {
            throw new IllegalStateException("Serializar altero los incidentes");
        }
        return "dimensiones, lugares y hallazgos separados; repeticion real agrupada; UUID completos y NBT conservados";
    }
}
