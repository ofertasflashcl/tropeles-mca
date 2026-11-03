package net.mca.entity.ai.nucleo;

import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.chatAI.ChatAI;
import net.mca.entity.ai.chatAI.MenteLocal;
import net.mca.entity.ai.mente.Iniciativa;
import net.mca.entity.ai.mente.Voluntad;
import net.mca.entity.ai.nucleo.Episodio.Estado;
import net.mca.entity.ai.nucleo.Episodio.Modo;
import net.mca.entity.ai.nucleo.Episodio.Ser;
import net.mca.entity.ai.nucleo.Episodio.Tipo;
import net.mca.entity.ai.relationship.AgeState;
import net.mca.entity.ai.sense.Retina;
import net.mca.entity.ai.sense.VisualMemory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.ai.brain.EntityLookTarget;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What a villager's inner life does among others. Gossip: a salient incident between people goes one hop to those who
 * care for the one it was done to (or trust the teller), as a rumour as believable as the teller is to them; a second
 * teller corroborates, never doubles. Comfort: one that cares for a neighbour in mourning (read from its public
 * trace) goes and stays with them a while, as much as it cares, minus what its duties ask. A warning: a monster it
 * sees close behind a player it likes. And it tells a player, when they look at it, what it heard of them, and asks
 * whether it is true. A connected villager's will already moves its body and speaks for it: comfort and warnings are
 * left to its own mind there.
 */
final class Social {
    private static final Map<Integer, Long> AVISADOS = new HashMap<>();
    private static final Map<UUID, Long> JUGADOR_AVISADO = new HashMap<>();

    private Social() {
    }

    static void tick(ServerWorld world, VillagerEntityMCA v, Mente m, long now) {
        if (v.isSleeping() || v.getAgeState() == AgeState.BABY) {
            return;
        }
        boolean conectado = Voluntad.conectado(v);
        if (!conectado && now >= m.proximoAviso) {
            avisar(world, v, m, now);
        }
        if (now >= m.proximoContar) {
            contarle(world, v, m, now);
        }
        if (now >= m.proximoEncuentro) {
            encuentro(world, v, m, now);
        }
        if (!conectado) {
            consolar(world, v, m, now);
        } else {
            m.consolando = null;
        }
        if (!m.pendientes.isEmpty() && now >= m.proximoPendientes) {
            m.proximoPendientes = now + 200;
            m.pendientes.entrySet().removeIf(e -> {
                ServerPlayerEntity p = world.getServer().getPlayerManager().getPlayer(e.getKey());
                if (p == null) {
                    return false;
                }
                v.getVillagerBrain().getMemoriesForPlayer(p).modHearts(e.getValue());
                return true;
            });
        }
    }

    static boolean idioma(ServerPlayerEntity p) {
        Boolean known = MenteLocal.playerSpanish(p.getUuid());
        return known == null || known;
    }

    // ------------------------------------------------------------------ gossip

    /** What it would tell: salient incidents between people, not false, not passed on too far or too often. */
    private static List<Episodio> contables(Mente m, long now, int n) {
        List<Episodio> out = new ArrayList<>();
        for (Episodio e : m.episodios) {
            if (!e.tipo.contable || !e.vivo() || e.saltos >= 2 || e.contado >= 3 || now - e.t1 > 72000) {
                continue;
            }
            float min = e.tipo == Tipo.MUERTE || e.tipo == Tipo.ROBO ? 0.5F : 0.6F;
            if (e.saliencia < min || e.tipo != Tipo.MUERTE && (e.quien == null || !e.quien.persona())) {
                continue;
            }
            out.add(e);
        }
        out.sort(Comparator.comparingDouble((Episodio e) -> e.saliencia * e.confianza).reversed());
        return out.subList(0, Math.min(n, out.size()));
    }

    /** One hop of news from {@code v} to the villagers within {@code radio} who care; how many were told something. */
    static int chismear(ServerWorld world, VillagerEntityMCA v, Mente m, long now, double radio, int max) {
        List<Episodio> news = contables(m, now, 3);
        if (news.isEmpty()) {
            return 0;
        }
        Ser teller = Ser.de(v);
        int told = 0;
        List<VillagerEntityMCA> near = world.getEntitiesByClass(VillagerEntityMCA.class, v.getBoundingBox().expand(radio),
                h -> h != v && h.isAlive() && h.getAgeState() != AgeState.BABY);
        near.sort(Comparator.comparingDouble(h -> h.squaredDistanceTo(v)));
        for (VillagerEntityMCA h : near) {
            if (told >= max) {
                break;
            }
            Mente hm = Nucleo.de(world, h);
            float bTeller = Nucleo.vinculo(world, h, hm, teller);
            boolean any = false;
            for (Episodio e : news) {
                if (oir(world, h, hm, e, teller, bTeller, now)) {
                    e.contado++;
                    any = true;
                }
            }
            if (any) {
                told++;
            }
        }
        if (told > 0) {
            m.ultimoSocial = now;
            m.sucio(now);
        }
        return told;
    }

    /**
     * {@code h} hears {@code e} from {@code teller}: kept when it cares for the one it was done to (or trusts the
     * teller), as believable as the teller is to it, never by the one who did it or had it done; a second, independent
     * teller corroborates it (the same teller, or one who has it from the same source, never counts twice). A death it hears of is mourned as much as it loved them.
     */
    static boolean oir(ServerWorld world, VillagerEntityMCA h, Mente hm, Episodio e, Ser teller, float bTeller, long now) {
        if (e.quien != null && e.quien.es(h.getUuid()) || e.aQuien != null && e.aQuien.es(h.getUuid())
                || e.aQuien == null && e.tipo != Tipo.MUERTE && teller.es(h.getUuid())) {
            return false;
        }
        Ser victima = e.aQuien == null ? teller : e.aQuien;
        float bVict = Nucleo.vinculo(world, h, hm, victima);
        if (bVict <= 0.3F && bTeller <= 0.5F) {
            return false;
        }
        float cred = Mente.clamp(0.3F + 0.45F * bTeller + 0.4F * (hm.credulidad - 0.5F), 0.1F, 0.95F);
        Episodio ya = hm.porOrigen(e.origen);
        if (ya != null) {
            // corroboration needs a new, independent account: not a teller heard before, nor one who has it from the same source
            boolean nueva = ya.modo == Modo.OIDO && ya.estado != Estado.FALSO && ya.apoyos < 5 && !ya.oyo(teller) && !ya.comparte(e);
            if (ya.modo == Modo.OIDO) {
                ya.anotar(teller, e);
                hm.sucio(now);
            }
            if (nueva) {
                ya.apoyos++;
                ya.confianza = 1.0F - (1.0F - ya.confianza) * (1.0F - Math.min(0.9F, e.confianza * cred * 0.7F));
                Nucleo.corazones(h, hm, ya, bVict);
                return true;
            }
            return false; // it knew already from this chain, or saw it itself
        }
        Episodio c = e.oido(hm.siguiente++, teller, cred);
        hm.anadir(c, now);
        hm.ultimoSocial = now;
        if (c.tipo == Tipo.MUERTE && c.aQuien != null && bVict > 0.5F) {
            Nucleo.duelo(hm, c.aQuien, 0.4F * bVict * c.confianza * (0.7F + 0.6F * hm.neuroticismo));
        }
        Nucleo.corazones(h, hm, c, bVict);
        return true;
    }

    /** By day: now and then, with a villager it meets close by, it shares the freshest news it has. */
    private static void encuentro(ServerWorld world, VillagerEntityMCA v, Mente m, long now) {
        m.proximoEncuentro = now + 200 + v.getRandom().nextInt(200);
        if (contables(m, now, 1).isEmpty()) {
            return;
        }
        List<VillagerEntityMCA> near = world.getEntitiesByClass(VillagerEntityMCA.class, v.getBoundingBox().expand(5),
                h -> h != v && h.isAlive() && !h.isSleeping() && h.getAgeState() != AgeState.BABY);
        VillagerEntityMCA h = near.stream().filter(x -> Retina.canSee(world, v, x)).min(Comparator.comparingDouble(x -> x.squaredDistanceTo(v))).orElse(null);
        if (h == null) {
            return;
        }
        Mente hm = Nucleo.de(world, h);
        Ser teller = Ser.de(v);
        float bTeller = Nucleo.vinculo(world, h, hm, teller);
        for (Episodio e : contables(m, now, 3)) {
            if (!oir(world, h, hm, e, teller, bTeller, now)) {
                continue;
            }
            e.contado++;
            m.ultimoSocial = now;
            m.proximoEncuentro = now + 2400;
            if (!Voluntad.conectado(v)) {
                v.getBrain().remember(MemoryModuleType.LOOK_TARGET, new EntityLookTarget(h, true));
            }
            if (!Voluntad.conectado(h)) {
                h.getBrain().remember(MemoryModuleType.LOOK_TARGET, new EntityLookTarget(v, true));
            }
            // a player close by overhears it
            if (world.getClosestPlayer(v, 10.0) instanceof ServerPlayerEntity p && !p.isSpectator()) {
                boolean es = idioma(p);
                boolean sobreTi = e.quien != null && e.quien.es(p.getUuid());
                String line = es ? h.getName().getString() + ", ¿supiste? " + Nucleo.mayuscula(e.narrar(true)) + (sobreTi ? "... y ahí está." : ".")
                        : h.getName().getString() + ", did you hear? " + Nucleo.mayuscula(e.narrar(false)) + (sobreTi ? "... and there they are." : ".");
                ChatAI.deliver(p, v, line);
            }
            return;
        }
    }

    // ------------------------------------------------------------------ comfort

    /**
     * It weighs going to sit with each neighbour it sees in mourning: care for them times their grief, less what its
     * duties ask, more for the warm-hearted, less the farther they are; above 0.22 it goes, and only changes whom
     * for one that beats them by 0.05. Close by, it says a word, eases their grief and stays a while.
     */
    private static void consolar(ServerWorld world, VillagerEntityMCA v, Mente m, long now) {
        if (m.consolando != null) {
            Entity e = world.getEntity(m.consolando);
            if (!(e instanceof VillagerEntityMCA n) || !n.isAlive() || now > m.consolandoHasta || v.getVillagerBrain().isPanicking()) {
                m.yaConsolado.put(m.consolando, now);
                m.consolando = null;
                return;
            }
            double d = v.squaredDistanceTo(n);
            v.getBrain().remember(MemoryModuleType.LOOK_TARGET, new EntityLookTarget(n, true));
            if (d > 6.25) {
                v.getBrain().remember(MemoryModuleType.WALK_TARGET, new WalkTarget(n.getBlockPos(), 0.5F, 2));
            } else if (!m.consolandoDijo) {
                m.consolandoDijo = true;
                Mente nm = Nucleo.de(world, n);
                String por = nm.dueloPor == null ? "" : nm.dueloPor.es();
                nm.impulsos[Mente.DUELO] = Math.max(0, nm.impulsos[Mente.DUELO] - 0.1F - 0.1F * m.afiliacion);
                nm.ultimoSocial = now;
                Episodio yo = m.nuevo(Tipo.CONSOLE, now, v.getUuid());
                yo.aQuien = Ser.de(n);
                yo.en(world, n.getBlockPos());
                yo.estado = Estado.CERRADO;
                m.registrar(yo, now);
                Episodio ella = nm.nuevo(Tipo.CONSOLE, now, n.getUuid());
                ella.quien = Ser.de(v);
                ella.en(world, n.getBlockPos());
                ella.estado = Estado.CERRADO;
                nm.registrar(ella, now);
                nm.hecho("trato:" + v.getUuid(), v.getName().getString(), now).apoyar(true, 0.5F, true, ella.id, now);
                m.ultimoSocial = now;
                if (!Voluntad.conectado(n)) {
                    n.getBrain().remember(MemoryModuleType.LOOK_TARGET, new EntityLookTarget(v, true));
                }
                if (world.getClosestPlayer(v, 12.0) instanceof ServerPlayerEntity p && !p.isSpectator()) {
                    boolean es = idioma(p);
                    String nn = n.getName().getString();
                    String[] lines = es
                            ? new String[]{nn + ", siento mucho lo de " + por + ". Aquí estoy.", "Ven, " + nn + ", siéntate un rato conmigo. " + por + " no querría verte así.",
                            "Aquí me tienes para lo que necesites, " + nn + "."}
                            : new String[]{nn + ", I'm so sorry about " + por + ". I'm here.", "Come sit with me a while, " + nn + ".", "You're not alone in this, " + nn + "."};
                    String line = lines[v.getRandom().nextInt(por.isEmpty() ? 1 : lines.length)];
                    if (por.isEmpty()) {
                        line = es ? nn + ", aquí estoy si me necesitas." : nn + ", I'm here if you need me.";
                    }
                    ChatAI.deliver(p, v, line);
                }
            }
            return;
        }
        if (now < m.proximoConsuelo) {
            return;
        }
        m.proximoConsuelo = now + 100;
        if (m.impulsos[Mente.DUELO] > 0.5F || m.impulsos[Mente.SEGURIDAD] > 0.4F || v.getVillagerBrain().isPanicking()) {
            return;
        }
        VillagerEntityMCA best = null;
        double bestU = 0.22;
        for (VillagerEntityMCA n : world.getEntitiesByClass(VillagerEntityMCA.class, v.getBoundingBox().expand(24),
                x -> x != v && x.isAlive() && !x.isSleeping())) {
            Nucleo.Traza t = Nucleo.TRAZAS.get(n.getUuid());
            if (t == null || t.duelo() < 0.25F || now - t.t() > 200 || t.dueloPor() != null && t.dueloPor().es(v.getUuid())) {
                continue;
            }
            Long last = m.yaConsolado.get(n.getUuid());
            if (last != null && now - last < 6000) {
                continue;
            }
            double u = Nucleo.vinculo(world, v, m, Ser.de(n)) * (0.4 + t.duelo()) * (1.0 - m.impulsos[Mente.DEBER] * 0.6) * (0.5 + 0.5 * m.afiliacion)
                    - Math.sqrt(v.squaredDistanceTo(n)) / 96.0;
            if (u > bestU + (best == null ? 0 : 0.05)) {
                bestU = u;
                best = n;
            }
        }
        if (best != null) {
            m.consolando = best.getUuid();
            m.consolandoHasta = now + 600 + Math.round(600 * m.afiliacion);
            m.consolandoDijo = false;
            if (m.yaConsolado.size() > 32) {
                m.yaConsolado.clear();
            }
        }
    }

    // ------------------------------------------------------------------ warnings

    /** A monster it sees close to a player it likes, where that player is not looking: it shouts which way. */
    private static void avisar(ServerWorld world, VillagerEntityMCA v, Mente m, long now) {
        m.proximoAviso = now + 20;
        for (VisualMemory.SeenEntity s : Retina.memory(v).entities(now, 40)) {
            if (s.type.getSpawnGroup() != SpawnGroup.MONSTER || s.pos == null) {
                continue;
            }
            Long done = AVISADOS.get(s.id);
            if (done != null && now - done < 600) {
                continue;
            }
            Entity mob = world.getEntityById(s.id);
            if (!(mob instanceof LivingEntity living) || !living.isAlive()) {
                continue;
            }
            PlayerEntity near = world.getClosestPlayer(mob, 10.0);
            if (!(near instanceof ServerPlayerEntity p) || p.isSpectator() || p.isCreative() || p.squaredDistanceTo(v) > 24 * 24
                    || Nucleo.vinculoJugador(v, p.getUuid()) < 0.1F) {
                continue;
            }
            Long lastP = JUGADOR_AVISADO.get(p.getUuid());
            if (lastP != null && now - lastP < 200) {
                continue;
            }
            boolean es = idioma(p);
            String lado = Iniciativa.side(p, mob.getPos(), es);
            boolean vaPorEl = mob instanceof MobEntity mobe && mobe.getTarget() == p;
            if (lado.equals(es ? "delante" : "ahead of you") && !vaPorEl) {
                continue; // it is in front of them: they see it
            }
            String[] n = Iniciativa.monsterName(mob.getType());
            String name = p.getName().getString();
            String line = m.riesgo > 0.6F
                    ? (es ? "¡" + name + ", cuidado! ¡" + Nucleo.mayuscula(n[0]) + " " + lado + "!" : name + ", look out! " + Nucleo.mayuscula(n[1]) + " " + lado + "!")
                    : (es ? "Ojo, " + name + ": " + n[0] + " " + lado + "." : "Heads up, " + name + ": " + n[1] + " " + lado + ".");
            ChatAI.deliver(p, v, line);
            AVISADOS.put(s.id, now);
            JUGADOR_AVISADO.put(p.getUuid(), now);
            if (AVISADOS.size() > 256) {
                AVISADOS.values().removeIf(t -> now - t > 600);
            }
            if (JUGADOR_AVISADO.size() > 64) {
                JUGADOR_AVISADO.values().removeIf(t -> now - t > 200);
            }
            Episodio e = m.nuevo(Tipo.AVISE, now, v.getUuid());
            e.quien = Ser.de(mob);
            e.aQuien = Ser.de(p);
            e.en(world, mob.getBlockPos());
            m.registrar(e, now);
            m.proximoAviso = now + 600;
            return;
        }
    }

    // ------------------------------------------------------------------ telling a player what it heard of them

    /**
     * A player looks at it from close: what it knows they did to someone else and has not told them yet, it tells
     * them, saying how it knows. A rumour ends in "¿es verdad?", and the answer is weighed ({@link Nucleo#respuesta}).
     */
    private static void contarle(ServerWorld world, VillagerEntityMCA v, Mente m, long now) {
        m.proximoContar = now + 40;
        if (!(world.getClosestPlayer(v, 6.0) instanceof ServerPlayerEntity p) || p.isSpectator()) {
            return;
        }
        Vec3d look = p.getRotationVec(1.0F);
        Vec3d to = v.getEyePos().subtract(p.getEyePos()).normalize();
        if (look.dotProduct(to) < 0.92 || !Retina.canSee(world, v, p)) {
            return;
        }
        Episodio best = null;
        for (Episodio e : m.episodios) {
            if (!e.vivo() || !e.tipo.contable || e.quien == null || !e.quien.es(p.getUuid()) || e.aQuien == null
                    || m.dichos.contains(e.origen) || e.saliencia < 0.45F || now - e.t1 > 72000) {
                continue;
            }
            if (best == null || e.saliencia * e.confianza > best.saliencia * best.confianza) {
                best = e;
            }
        }
        if (best == null) {
            return;
        }
        boolean es = idioma(p);
        String name = p.getName().getString();
        String line;
        if (best.valencia < 0) {
            if (best.modo == Modo.OIDO) {
                String quien = best.fuente == null ? (es ? "me enteré de" : "I heard") : (es ? "me contó " + best.fuente.es() : best.fuente.en() + " told me");
                line = es ? "Oye " + name + ", " + quien + " que " + best.narrarATi(true) + ". ¿Es verdad?"
                        : "Hey " + name + ", " + quien + " that " + best.narrarATi(false) + ". Is that true?";
                m.preguntaA = p.getUuid();
                m.preguntaOrigen = best.origen;
                m.preguntaHasta = now + 2400;
                m.preguntaLinea = line;
            } else {
                line = es ? name + ", te vi: " + best.narrarATi(true) + ". No me gustó nada." : name + ", I saw it: " + best.narrarATi(false) + ". I didn't like that at all.";
            }
        } else {
            line = best.modo == Modo.OIDO
                    ? (es ? "Me contaron que " + best.narrarATi(true) + ". ¡Bien ahí, " + name + "!" : "I heard " + best.narrarATi(false) + ". Nice one, " + name + "!")
                    : (es ? "Vi que " + best.narrarATi(true) + ". Eres buena gente, " + name + "." : "I saw " + best.narrarATi(false) + ". You're good people, " + name + ".");
        }
        ChatAI.deliver(p, v, line);
        m.dichos.add(best.origen);
        Mente.limitar(m.dichos);
        Episodio d = m.nuevo(Tipo.DIJE, now, v.getUuid());
        d.aQuien = Ser.de(p);
        d.cosa = "le dije a " + name + ": «" + line + "»";
        d.causas = new int[]{best.id};
        d.estado = Estado.CERRADO;
        m.registrar(d, now);
        m.ultimoSocial = now;
        m.proximoContar = now + 2400;
    }
}
