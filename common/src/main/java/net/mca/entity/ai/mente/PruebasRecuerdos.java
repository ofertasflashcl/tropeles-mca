package net.mca.entity.ai.mente;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** In-game fixture for the real Recuerdos archive; no fabricated entities need to tick. */
public final class PruebasRecuerdos {
    private static final UUID DIARIO = UUID.nameUUIDFromBytes("tropeles-diario-paginado-ci".getBytes(StandardCharsets.UTF_8));
    private PruebasRecuerdos() { }
    public static int run(ServerWorld world, boolean prepare) {
        var store = Recuerdos.Registro.get(world);
        var lessons = Conciencia.Registro.get(world.getServer());
        int count = 0;
        for (int i = 0; i < 1000; i++) {
            UUID id = UUID.nameUUIDFromBytes(("tropeles-archivo-ci-" + i).getBytes(StandardCharsets.UTF_8));
            Recuerdos memory = store.of(id);
            BlockPos target = new BlockPos(i, 70, -i);
            if (prepare) memory.sitio("hogar-ci", target, world.getTime());
            var known = lessons.de(id);
            String lesson = "leccion-ci-" + i;
            if (prepare) {
                lessons.lecciones.put(id, new java.util.ArrayList<>(java.util.List.of(lesson)));
                lessons.changed(id);
                lessons.trim(id);
                known = lessons.de(id);
            }
            if (target.equals(memory.sitio("hogar-ci")) && known != null && known.contains(lesson)) count++;
        }
        if (count != 1000) throw new IllegalStateException("Missing spatial memories: " + count + "/1000");
        return count;
    }

    /** More lifetime entries than the hot cache; the second process only reads the same saved identity. */
    public static String cronica(ServerWorld world, boolean prepare) {
        Recuerdos memory = Recuerdos.Registro.get(world).of(DIARIO);
        if (prepare) {
            for (int i = 0; i < 900; i++) {
                memory.anotar(world.getTime(), i == 0 ? "charla" : "oficio", i == 0 ? "promesa-ci-antigua" : "oficio-ci-" + i, new BlockPos(i, 70, 0));
            }
            memory.toNbt(); // same checkpoint path as the world's save, including a partial final batch
        }
        if (memory.siguienteHecho() != 900 || memory.hechosSinGuardar() != 0 || memory.hechosEnRam() > Recuerdos.MAX_HECHOS) {
            throw new IllegalStateException("Diario no acotado/completo: " + memory.hechosEnRam() + "/" + memory.siguienteHecho());
        }
        var first = memory.historial(1, 1);
        var rare = memory.hechos("charla", 1);
        if (first.size() != 1 || !first.get(0).texto().equals("promesa-ci-antigua") || rare.size() != 1 || rare.get(0).orden() != 0) {
            throw new IllegalStateException("No recupera el primer recuerdo o la charla antigua");
        }
        java.util.Set<Long> ids = new java.util.HashSet<>();
        long cursor = Long.MAX_VALUE;
        while (true) {
            var page = memory.historial(cursor, 128);
            if (page.isEmpty()) break;
            for (var h : page) {
                if (!ids.add(h.orden()) || h.orden() >= cursor) throw new IllegalStateException("Paginacion duplicada/desordenada");
            }
            cursor = page.get(0).orden();
        }
        if (ids.size() != 900 || !ids.contains(899L)) throw new IllegalStateException("Faltan recuerdos: " + ids.size());
        return "900/900 recuerdos reales, primero y charla antigua recuperados; RAM=" + memory.hechosEnRam() + "; " + (prepare ? "escrito en paginas" : "leido sin recrear tras reinicio");
    }
}
