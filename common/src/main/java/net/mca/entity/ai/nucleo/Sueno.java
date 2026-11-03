package net.mca.entity.ai.nucleo;

import net.mca.MCA;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.nucleo.Episodio.Estado;
import net.mca.entity.ai.nucleo.Episodio.Modo;
import net.mca.entity.ai.nucleo.Episodio.Tipo;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What sleep does with the day (the slow loop of NÚCLEO-MC): what is over is closed; every incident with someone
 * becomes evidence for or against them, every attack by a creature marks the place as dangerous; three or more of the
 * same thing by the same one in the same place become one pattern, told by one summary that absorbs the small ones;
 * the news goes one hop to those sleeping near who care; and temperament drifts a hair with what it lived. Once a
 * night after 10 seconds asleep, or after a long busy day without sleep; one mind per server tick at most.
 */
final class Sueno {
    private static long ultimoTick = -1;

    private Sueno() {
    }

    static void quizas(ServerWorld world, VillagerEntityMCA v, Mente m, long now) {
        boolean durmiendo = v.isSleeping();
        if (durmiendo) {
            if (m.dormidoDesde < 0) {
                m.dormidoDesde = now;
            }
        } else {
            m.dormidoDesde = -1;
        }
        long dia = world.getTimeOfDay() / 24000L;
        boolean toca = durmiendo && now - m.dormidoDesde >= 200 && m.ultimoDia != dia
                || m.nuevos >= 48 && now - m.ultimaConsolidacion >= 24000;
        if (!toca || ultimoTick == now) {
            return;
        }
        ultimoTick = now;
        if (durmiendo) {
            m.ultimoDia = dia;
        }
        consolidar(world, v, m, now);
    }

    static void consolidar(ServerWorld world, VillagerEntityMCA v, Mente m, long now) {
        m.ultimaConsolidacion = now;
        m.nuevos = 0;
        float bueno = 0;
        float malo = 0;

        for (Episodio e : m.episodios) {
            // 1. what is over is closed
            if (e.estado == Estado.ABIERTO && now - e.t1 > Math.max(600L, 2L * e.tipo.ventana)) {
                e.estado = Estado.CERRADO;
            }
            if (e.digerido || e.estado != Estado.CERRADO) {
                continue;
            }
            // 2. evidence about people, danger in places
            e.digerido = true;
            if (e.valencia < 0) {
                malo += -e.valencia * e.saliencia;
            } else {
                bueno += e.valencia * e.saliencia;
            }
            if (e.tipo.contable && e.quien != null && e.quien.persona() && e.quien.id() != null && !e.quien.es(v.getUuid())) {
                m.hecho("trato:" + e.quien.id(), e.quien.es(), now)
                        .apoyar(e.valencia > 0, Math.abs(e.valencia) * e.confianza, e.modo == Modo.VISTO, e.id, now);
            }
            if (e.quien != null && e.quien.clase() == Episodio.BICHO && e.donde != null
                    && (e.tipo == Tipo.HERIDO || e.tipo == Tipo.HUI || e.tipo == Tipo.VIO_HERIR || e.tipo == Tipo.PELEA)) {
                BlockPos c = e.donde;
                m.hecho("peligro:" + (e.dim.isEmpty() ? "" : e.dim + "|") + (c.getX() >> 4) + "," + (c.getY() >> 4) + "," + (c.getZ() >> 4),
                        e.quien.es() + " cerca de " + e.dondeTexto(true), now)
                        .apoyar(true, e.confianza, e.modo == Modo.VISTO, e.id, now);
            }
        }

        // 3. patterns: the same thing, by the same one, in the same place
        Map<String, List<Episodio>> grupos = new HashMap<>();
        for (Episodio e : m.episodios) {
            if (e.estado != Estado.CERRADO || e.resumen != 0 || e.modo != Modo.VISTO || now - e.t1 > 24000L * 7
                    || e.tipo == Tipo.RESUMEN || e.tipo == Tipo.REFLEXION || e.tipo == Tipo.DIJE || e.tipo == Tipo.MUERTE) {
                continue;
            }
            String key = "patron:" + e.tipo.name() + ":" + (e.quien == null ? "-" : e.quien.clave()) + ":" + e.lugar();
            grupos.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }
        int patrones = 0;
        for (Map.Entry<String, List<Episodio>> g : grupos.entrySet()) {
            Hecho previo = m.hechos.get(g.getKey());
            Episodio resumen = previo == null || previo.resumen == 0 ? null : m.porId(previo.resumen);
            if (g.getValue().size() < (resumen == null ? 3 : 1)) {
                continue;
            }
            patrones++;
            Episodio first = g.getValue().get(0);
            Hecho h = m.hecho(g.getKey(), "", now);
            float val = 0;
            float sal = 0;
            List<Integer> ids = new ArrayList<>();
            if (resumen != null) {
                for (int id : resumen.causas) {
                    ids.add(id);
                }
            }
            for (Episodio e : g.getValue()) {
                h.apoyar(true, e.confianza, true, e.id, now);
                val += e.valencia;
                sal = Math.max(sal, e.saliencia);
                if (!ids.contains(e.id) && ids.size() < 32) {
                    ids.add(e.id);
                }
            }
            int veces = Math.round(h.pos);
            if (resumen == null) {
                resumen = m.nuevo(Tipo.RESUMEN, now, v.getUuid());
                resumen.quien = first.quien;
                resumen.donde = first.donde;
                resumen.dim = first.dim;
                resumen.estado = Estado.CERRADO;
                resumen.digerido = true;
                resumen.valencia = Mente.clamp(val / g.getValue().size(), -1.0F, 1.0F);
                resumen.saliencia = Math.max(0.35F, sal);
                m.anadir(resumen, now);
                h.resumen = resumen.id;
            } else {
                resumen.t1 = now;
                resumen.saliencia = Math.min(1.0F, Math.max(resumen.saliencia, sal) + 0.05F);
            }
            resumen.veces = veces;
            resumen.cosa = patron(first, veces, true);
            resumen.cosaEn = patron(first, veces, false);
            resumen.causas = ids.stream().mapToInt(Integer::intValue).toArray();
            h.valor = resumen.cosa;
            for (Episodio e : g.getValue()) {
                e.resumen = resumen.id;
                if (e.saliencia < 0.5F) {
                    e.estado = Estado.ABSORBIDO; // the summary tells it now
                }
            }
        }

        // 4. the news, one hop, to those near that care
        int contados = Social.chismear(world, v, m, now, 24, 4);

        // 5. a hair of drift: a hard day makes it a little more anxious and a little colder, a good one the opposite
        float drift = Mente.clamp(0.01F * (malo - bueno), -0.01F, 0.01F);
        m.neuroticismo += drift;
        m.afiliacion -= drift * 0.5F;
        m.acotar();
        m.sucio(now);
        if (patrones > 0 || contados > 0) {
            MCA.LOGGER.info("[Tropeles] {} durmió y ordenó su memoria: {} episodios, {} creencias, {} patrones, {} chismes contados",
                    v.getName().getString(), m.episodios.size(), m.hechos.size(), patrones, contados);
        }
    }

    /** "Benja me ha pegado 4 veces por 102 64 -31", "me han atacado 3 veces cerca de ...". */
    private static String patron(Episodio e, int veces, boolean es) {
        String q = e.quien == null ? "" : e.quien.nombre(es);
        String at = e.donde == null ? "" : (es ? " cerca de " : " near ") + e.dondeTexto(es);
        return switch (e.tipo) {
            case HERIDO -> e.quien != null && e.quien.clase() == Episodio.BICHO
                    ? (es ? "me han atacado " + veces + " veces" + at + " (" + q + ")" : "I've been attacked " + veces + " times" + at + " (" + q + ")")
                    : (es ? q + " me ha pegado " + veces + " veces" + at : q + " has hit me " + veces + " times" + at);
            case VIO_HERIR -> es ? "he visto a " + q + " pegarle a gente " + veces + " veces" + at : "I've seen " + q + " hit people " + veces + " times" + at;
            case REGALO -> es ? q + " me ha hecho " + veces + " regalos" : q + " has given me " + veces + " gifts";
            case ROBO -> es ? q + " me ha robado " + veces + " veces" : q + " has stolen from me " + veces + " times";
            case HUI -> es ? "he tenido que huir " + veces + " veces" + at : "I've had to run " + veces + " times" + at;
            case PELEA -> es ? "he peleado " + veces + " veces" + at : "I've fought " + veces + " times" + at;
            case HALLAZGO -> es ? "he encontrado cosas " + veces + " veces" + at : "I've found things " + veces + " times" + at;
            case CONSOLE -> es ? "he acompañado a gente en su pena " + veces + " veces" : "I've comforted people " + veces + " times";
            default -> e.narrar(es) + (es ? " (" + veces + " veces)" : " (" + veces + " times)");
        };
    }
}
