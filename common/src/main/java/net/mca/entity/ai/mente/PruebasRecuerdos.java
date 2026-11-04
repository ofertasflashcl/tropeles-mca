package net.mca.entity.ai.mente;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** In-game fixture for the real Recuerdos archive; no fabricated entities need to tick. */
public final class PruebasRecuerdos {
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
}
