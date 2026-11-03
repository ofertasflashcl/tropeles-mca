package net.mca.entity.ai.nucleo;

import net.mca.MCA;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Chore;
import net.mca.entity.ai.Memories;
import net.mca.entity.ai.body.Mining;
import net.mca.entity.ai.brain.tasks.chore.ProspectingTask;
import net.mca.entity.ai.chatAI.Acciones;
import net.mca.entity.ai.chatAI.MenteLocal;
import net.mca.entity.ai.mente.Voluntad;
import net.mca.entity.ai.nucleo.Episodio.Estado;
import net.mca.entity.ai.nucleo.Episodio.Modo;
import net.mca.entity.ai.nucleo.Episodio.Ser;
import net.mca.entity.ai.nucleo.Episodio.Tipo;
import net.mca.entity.ai.relationship.AgeState;
import net.mca.entity.ai.sense.Retina;
import net.mca.entity.ai.sense.VisualMemory;
import net.mca.server.world.data.FamilyTreeNode;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

/**
 * The core of a villager's inner life, after the NÚCLEO-MC design and its audit: what happens between people is kept
 * as incidents with how they are known ({@link Episodio}), beliefs grow from them with evidence for and against
 * ({@link Hecho}), eight drives and four slots of attention run once a second within a hard time budget, sleep
 * consolidates the day into patterns and passes the news on one hop ({@link Sueno}), and others can read what holds
 * it (its public trace), so a neighbour may come and sit with one in mourning ({@link Social}). What a language model
 * says is held to what it was told ({@link Anclaje}).
 * <p>
 * Every hook here runs on the server thread and never throws.
 */
public final class Nucleo {
    private static final Map<Entity, Mente> VIVAS = new WeakHashMap<>();
    /** What each villager shows of itself to others: grief and for whom, what holds its attention. Server thread. */
    static final Map<UUID, Traza> TRAZAS = new HashMap<>();
    /** All the minds together get this much of each server tick; the rest wait for their next turn. */
    private static final long PRESUPUESTO_NS = 2_000_000L;
    private static long tickActual = -1;
    private static long gastado;
    private static long ultimaFalla;

    private static final Pattern NIEGA = Pattern.compile("(?<![a-z])(no|nop|nones|mentira|falso|nunca|jamas|para nada|no es verdad|no es cierto|"
            + "no fui yo|calumnia|chisme|nope|lie|lies|false|never|not true|wasn t me|wasnt me)(?![a-z])");
    private static final Pattern ADMITE = Pattern.compile("(?<![a-z])(si|sip|sii|es verdad|es cierto|cierto|verdad|lo admito|fui yo|claro|obvio|"
            + "yes|yeah|yep|true|i did|it was me)(?![a-z])");

    /** What others can see of it. */
    record Traza(UUID id, String nombre, float duelo, @Nullable Ser dueloPor, String foco, long t) {
    }

    private Nucleo() {
    }

    // ------------------------------------------------------------------ finding a mind

    static Mente de(ServerWorld world, VillagerEntityMCA v) {
        Mente m = VIVAS.get(v);
        if (m != null && m.mundo != null && m.mundo.vivas.get(v.getUuid()) == m) {
            m.tocado = Math.max(m.tocado, world.getTime()); // in use: the last to leave RAM
            return m;
        }
        m = NucleoMundo.get(world).of(v.getUuid(), world.getTime());
        if (!m.rasgos) {
            m.rasgos(world.getSeed(), v.getUuid(), v.getVillagerBrain().getPersonality());
            m.ultimoSocial = world.getTime();
            m.ultimoHallazgo = world.getTime();
        }
        VIVAS.put(v, m);
        return m;
    }

    private static void falla(String que, Entity v, RuntimeException e) {
        long now = System.currentTimeMillis();
        if (now - ultimaFalla > 10_000) {
            ultimaFalla = now;
            MCA.LOGGER.warn("[Tropeles] el núcleo de {} falló con {}: {}", v.getName().getString(), que, e.toString());
        }
    }

    // ------------------------------------------------------------------ what happens to it

    /** It was hurt: pain, and when someone did it, an incident it lived, and one for each villager who saw it. */
    public static void herido(VillagerEntityMCA v, DamageSource source, float dano) {
        if (!(v.getWorld() instanceof ServerWorld world) || dano <= 0) {
            return;
        }
        try {
            long now = world.getTime();
            Mente m = de(world, v);
            m.dolorHasta = now + 200;
            m.impulsos[Mente.DOLOR] = 1.0F;
            Entity atacante = source.getAttacker();
            if (atacante == null || atacante == v) {
                return;
            }
            Ser quien = Ser.de(atacante);
            if (quien.clase() == Episodio.BICHO) {
                m.amenaza = quien.es();
                m.amenazaT = now;
            }
            Episodio e = m.nuevo(Tipo.HERIDO, now, v.getUuid());
            e.quien = quien;
            e.cantidad = dano;
            e.valencia = -Mente.clamp(0.3F + dano / 10.0F, 0.3F, 1.0F);
            e.en(v.getWorld(), Mining.pies(v));
            Episodio kept = m.registrar(e, now);
            Ser victima = Ser.de(v);
            if (kept == e) {
                for (VillagerEntityMCA w : testigos(world, v, 16, 8)) {
                    vioHerir(world, w, quien, victima, dano, e.donde, now);
                    e.testigos.add(w.getUuid());
                }
            } else {
                // the same ones keep watching it go on
                for (UUID id : kept.testigos) {
                    if (world.getEntity(id) instanceof VillagerEntityMCA w && w.isAlive()) {
                        vioHerir(world, w, quien, victima, dano, e.donde, now);
                    }
                }
            }
        } catch (RuntimeException ex) {
            falla("un golpe", v, ex);
        }
    }

    private static void vioHerir(ServerWorld world, VillagerEntityMCA w, Ser quien, Ser victima, float dano, @Nullable BlockPos donde, long now) {
        Mente wm = de(world, w);
        Episodio we = wm.nuevo(Tipo.VIO_HERIR, now, w.getUuid());
        we.quien = quien;
        we.aQuien = victima;
        we.cantidad = dano;
        we.en(world, donde);
        float b = vinculo(world, w, wm, victima);
        we.valencia = -Mente.clamp(0.2F + 0.6F * Math.max(0, b) + dano / 20.0F, 0.2F, 1.0F);
        wm.registrar(we, now);
    }

    /** Villagers who can see {@code v} right now, nearest first. */
    static List<VillagerEntityMCA> testigos(ServerWorld world, VillagerEntityMCA v, double radio, int max) {
        List<VillagerEntityMCA> near = world.getEntitiesByClass(VillagerEntityMCA.class, v.getBoundingBox().expand(radio),
                w -> w != v && w.isAlive() && !w.isSleeping() && w.getAgeState() != AgeState.BABY);
        near.sort(Comparator.comparingDouble(w -> w.squaredDistanceTo(v)));
        List<VillagerEntityMCA> out = new ArrayList<>(max);
        for (VillagerEntityMCA w : near) {
            if (out.size() >= max) {
                break;
            }
            if (Retina.canSee(world, w, v)) {
                out.add(w);
            }
        }
        return out;
    }

    /**
     * It died: those who saw it keep the death (the assault they saw is folded into it) and mourn it as much as they
     * loved it; its family hears of it wherever they are.
     */
    public static void murio(VillagerEntityMCA v, DamageSource cause) {
        if (!(v.getWorld() instanceof ServerWorld world)) {
            return;
        }
        try {
            long now = world.getTime();
            Entity killer = cause.getAttacker();
            Ser victima = Ser.de(v);
            Ser asesino = killer == null || killer == v ? null : Ser.de(killer);
            BlockPos donde = v.getBlockPos();
            Set<UUID> saben = new HashSet<>();
            for (VillagerEntityMCA w : testigos(world, v, 24, 12)) {
                luto(world, w, victima, asesino, Modo.VISTO, donde, now);
                saben.add(w.getUuid());
            }
            for (UUID id : parientes(v.getRelationships().getFamilyEntry())) {
                if (!saben.contains(id) && world.getEntity(id) instanceof VillagerEntityMCA w && w.isAlive()) {
                    luto(world, w, victima, asesino, Modo.OIDO, donde, now);
                }
            }
        } catch (RuntimeException ex) {
            falla("una muerte", v, ex);
        }
    }

    private static void luto(ServerWorld world, VillagerEntityMCA w, Ser victima, @Nullable Ser asesino, Modo modo, BlockPos donde, long now) {
        Mente wm = de(world, w);
        Episodio e = wm.nuevo(Tipo.MUERTE, now, w.getUuid());
        e.aQuien = victima;
        e.quien = asesino;
        e.modo = modo;
        e.en(world, donde);
        e.confianza = modo == Modo.VISTO ? 1.0F : 0.9F;
        List<Integer> causas = new ArrayList<>();
        for (Episodio o : wm.episodios) {
            if (o.tipo == Tipo.VIO_HERIR && o.vivo() && o.aQuien != null && o.aQuien.mismo(victima) && now - o.t1 < 2400) {
                o.estado = Estado.ABSORBIDO;
                causas.add(o.id);
            }
        }
        e.causas = causas.stream().mapToInt(Integer::intValue).toArray();
        float b = vinculo(world, w, wm, victima);
        e.valencia = -Mente.clamp(0.4F + 0.6F * Math.max(0, b), 0.4F, 1.0F);
        e.estado = Estado.CERRADO;
        wm.registrar(e, now);
        if (b > 0.5F) {
            duelo(wm, victima, 0.4F * b * (0.7F + 0.6F * wm.neuroticismo));
        }
    }

    static void duelo(Mente m, Ser por, float cuanto) {
        m.impulsos[Mente.DUELO] = Math.min(1.0F, m.impulsos[Mente.DUELO] + cuanto);
        m.dueloPor = por;
    }

    static Set<UUID> parientes(FamilyTreeNode node) {
        Set<UUID> out = new HashSet<>(node.children());
        out.addAll(node.siblings());
        out.add(node.partner());
        out.add(node.father());
        out.add(node.mother());
        out.remove(node.id());
        return out;
    }

    /** A gift it took from {@code player}: a good thing it lived with them. */
    public static void regalo(VillagerEntityMCA v, PlayerEntity player, ItemStack stack, boolean mejor) {
        if (!(v.getWorld() instanceof ServerWorld world) || stack.isEmpty()) {
            return;
        }
        try {
            long now = world.getTime();
            Mente m = de(world, v);
            Episodio e = m.nuevo(Tipo.REGALO, now, v.getUuid());
            e.quien = Ser.de(player);
            e.cosa = MenteLocal.spanishName(stack);
            e.cosaEn = stack.getName().getString().toLowerCase(Locale.ROOT);
            e.cantidad = 1;
            e.valencia = mejor ? 0.8F : 0.5F;
            e.en(v.getWorld(), Mining.pies(v));
            m.registrar(e, now);
            m.ultimoSocial = now;
        } catch (RuntimeException ex) {
            falla("un regalo", v, ex);
        }
    }

    /** Its chest was robbed by {@code thief}: seen at the chest, or worked out afterwards. */
    public static void robo(VillagerEntityMCA v, PlayerEntity thief, Item item, int n, boolean visto) {
        if (!(v.getWorld() instanceof ServerWorld world)) {
            return;
        }
        try {
            long now = world.getTime();
            Mente m = de(world, v);
            Episodio e = m.nuevo(Tipo.ROBO, now, v.getUuid());
            e.quien = Ser.de(thief);
            e.cantidad = n;
            ItemStack stack = new ItemStack(item);
            e.cosa = MenteLocal.spanishName(stack);
            e.cosaEn = stack.getName().getString().toLowerCase(Locale.ROOT);
            e.modo = visto ? Modo.VISTO : Modo.DEDUCIDO;
            e.confianza = visto ? 1.0F : 0.85F;
            e.valencia = -Mente.clamp(0.4F + n / 32.0F, 0.4F, 1.0F);
            e.en(v.getWorld(), Mining.pies(v));
            m.registrar(e, now);
        } catch (RuntimeException ex) {
            falla("un robo", v, ex);
        }
    }

    /** It is backing away from {@code from}; caused by the last hit, when there was one. Cheap while it goes on. */
    public static void huye(VillagerEntityMCA v, LivingEntity from, String porque) {
        if (!(v.getWorld() instanceof ServerWorld world)) {
            return;
        }
        try {
            long now = world.getTime();
            Mente m = de(world, v);
            Ser quien = Ser.de(from);
            Episodio last = m.ultimo(Tipo.HUI, null);
            if (last != null && last.estado == Estado.ABIERTO && now - last.t1 <= Tipo.HUI.ventana && quien.mismo(last.quien)) {
                last.t1 = now;
                return;
            }
            Episodio e = m.nuevo(Tipo.HUI, now, v.getUuid());
            e.quien = quien;
            e.cosa = porque;
            e.en(v.getWorld(), Mining.pies(v));
            Episodio hit = m.ultimo(Tipo.HERIDO, null);
            if (hit != null && now - hit.t1 < 600) {
                e.causas = new int[]{hit.id};
            }
            m.registrar(e, now);
            m.amenaza = quien.es();
            m.amenazaT = now;
        } catch (RuntimeException ex) {
            falla("una huida", v, ex);
        }
    }

    /** What it noted in its own memories ({@link net.mca.entity.ai.mente.Recuerdos}) that is an incident too. */
    public static void desdeRecuerdo(LivingEntity villager, String tipo, String texto, @Nullable BlockPos donde) {
        Tipo t = switch (tipo) {
            case "hallazgo" -> Tipo.HALLAZGO;
            case "pelea" -> Tipo.PELEA;
            case "encargo" -> Tipo.CUMPLI;
            case "obra" -> Tipo.OBRA;
            case "dar" -> Tipo.DI;
            default -> null;
        };
        if (t == null || !(villager instanceof VillagerEntityMCA v) || !(v.getWorld() instanceof ServerWorld world)) {
            return;
        }
        try {
            long now = world.getTime();
            Mente m = de(world, v);
            Episodio e = m.nuevo(t, now, v.getUuid());
            e.cosa = texto.length() > 160 ? texto.substring(0, 160) + "…" : texto;
            e.en(villager.getWorld(), donde);
            m.registrar(e, now);
            if (t == Tipo.HALLAZGO) {
                m.ultimoHallazgo = now;
            }
        } catch (RuntimeException ex) {
            falla("un recuerdo", v, ex);
        }
    }

    /**
     * A lesson its inner voice drew, kept as a belief only when it cites incidents it really has ("#12 #15"): a
     * reflection that rests on nothing it lived is not one. True when kept.
     */
    public static boolean reflexion(VillagerEntityMCA v, String leccion, String cita) {
        if (!(v.getWorld() instanceof ServerWorld world) || leccion.isBlank() || cita == null || cita.isBlank()) {
            return false;
        }
        try {
            long now = world.getTime();
            Mente m = de(world, v);
            List<Integer> ids = new ArrayList<>();
            float conf = 0;
            java.util.Set<String> raices = raices(leccion);
            java.util.regex.Matcher mm = Pattern.compile("\\d+").matcher(cita);
            while (mm.find() && ids.size() < 6) {
                try {
                    Episodio e = m.porId(Integer.parseInt(mm.group()));
                    // citing an incident is not enough: it has to be about what the lesson says (a person, a thing, a deed)
                    if (e != null && e.tipo != Tipo.DIJE && !ids.contains(e.id)
                            && !java.util.Collections.disjoint(raices, raices(e.narrar(true) + " " + e.narrar(false) + " " + e.cosa + " " + e.cosaEn))) {
                        ids.add(e.id);
                        conf += e.confianza * (e.estado == Estado.FALSO ? 0.2F : 1.0F);
                    }
                } catch (NumberFormatException ignored) {
                    // not an id
                }
            }
            if (ids.isEmpty()) {
                return false;
            }
            Episodio r = m.nuevo(Tipo.REFLEXION, now, v.getUuid());
            r.cosa = leccion;
            r.causas = ids.stream().mapToInt(Integer::intValue).toArray();
            r.confianza = conf / ids.size();
            r.estado = Estado.CERRADO;
            m.registrar(r, now);
            return true;
        } catch (RuntimeException ex) {
            falla("una reflexión", v, ex);
            return false;
        }
    }

    private static final Set<String> VACIAS = Set.of("para", "como", "pero", "esta", "este", "esto", "esas", "esos", "porq", "cuan",
            "todo", "toda", "algo", "nada", "sien", "tien", "hace", "siem", "nunc", "debo", "debe", "mejo", "much", "poco", "otra", "otro",
            "with", "that", "this", "have", "from", "when", "they", "what", "will", "just", "been", "abou", "ther", "some", "more", "alwa",
            "neve", "shou", "vece", "time");

    /** The first four letters of each word of four or more, without the empty ones: enough to tell what a text is about. */
    private static Set<String> raices(String text) {
        Set<String> out = new java.util.HashSet<>();
        for (String w : net.mca.entity.ai.chatAI.Acciones.normalize(text).split("[^a-z0-9]+")) {
            if (w.length() >= 4 && !VACIAS.contains(w.substring(0, 4))) {
                out.add(w.substring(0, 4));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ once a second

    /** Drives, attention, sleep's work and what it does with others: once a second per villager, within the budget. */
    public static void tick(ServerWorld world, VillagerEntityMCA v) {
        long now = world.getTime();
        if ((now + v.getId()) % 20 != 0) {
            return;
        }
        if (now != tickActual) {
            tickActual = now;
            gastado = 0;
        }
        if (gastado > PRESUPUESTO_NS) {
            return; // out of time this tick: it thinks again in a second
        }
        long start = System.nanoTime();
        try {
            Mente m = de(world, v);
            impulsos(world, v, m, now);
            enfocar(v, m, now);
            Sueno.quizas(world, v, m, now);
            Social.tick(world, v, m, now);
        } catch (RuntimeException e) {
            falla("pensar", v, e);
        } finally {
            gastado += System.nanoTime() - start;
        }
    }

    private static void impulsos(ServerWorld world, VillagerEntityMCA v, Mente m, long now) {
        float[] d = m.impulsos;
        boolean conectado = Voluntad.conectado(v);
        d[Mente.HAMBRE] = conectado ? Mente.clamp(1.0F - ProspectingTask.of(v).food() / 20.0F, 0.0F, 1.0F) : 0.1F;
        long hora = world.getTimeOfDay() % 24000L;
        boolean noche = hora >= 13000 && hora < 23000;
        d[Mente.CANSANCIO] = v.isSleeping() ? Math.max(0, d[Mente.CANSANCIO] - 0.02F)
                : noche ? Math.min(1.0F, d[Mente.CANSANCIO] + 0.006F) : Math.max(0, d[Mente.CANSANCIO] - 0.002F);
        float miedo = 0;
        for (VisualMemory.SeenEntity s : Retina.memory(v).entities(now, 40)) {
            if (s.type.getSpawnGroup() == SpawnGroup.MONSTER && s.distance < 12) {
                float f = (float) (1.0 - s.distance / 12.0);
                if (f > miedo) {
                    miedo = f;
                    m.amenaza = Episodio.nombre(s.type)[0];
                    m.amenazaT = now;
                }
            }
        }
        if (now < m.dolorHasta) {
            miedo = Math.max(miedo, 0.6F);
        }
        d[Mente.SEGURIDAD] = Math.max(Mente.clamp(miedo * (0.6F + 0.8F * m.riesgo), 0.0F, 1.0F), d[Mente.SEGURIDAD] - 0.08F);
        d[Mente.AFILIACION] = Mente.clamp((now - m.ultimoSocial) / 24000.0F * (0.6F + 0.8F * m.gregarismo), 0.0F, 1.0F);
        if (conectado) {
            Voluntad.Modo modo = Voluntad.of(v).modo();
            d[Mente.DEBER] = modo == Voluntad.Modo.ENCARGO || modo == Voluntad.Modo.OBRA ? 0.85F
                    : modo == Voluntad.Modo.SIGUIENDO || modo == Voluntad.Modo.ESPERANDO ? 0.7F : 0.5F;
        } else {
            d[Mente.DEBER] = v.getVillagerBrain().getCurrentJob() != Chore.NONE ? 0.6F : 0.15F;
        }
        d[Mente.CURIOSIDAD] = Mente.clamp(0.2F + (now - m.ultimoHallazgo) / 48000.0F * 0.6F, 0.0F, 1.0F);
        d[Mente.DUELO] = Math.max(0, d[Mente.DUELO] - 0.000125F); // 0.15 a day
        if (d[Mente.DUELO] < 0.01F) {
            m.dueloPor = null;
        }
        d[Mente.DOLOR] = now < m.dolorHasta ? 1.0F : Math.max(0, d[Mente.DOLOR] - 0.1F);
    }

    /**
     * The four things that hold it now, each weighed less the longer it has held it (0.1 a minute, up to 0.5), the
     * first one its attention, which only moves to another that beats it by 0.05; and its public trace.
     */
    private static void enfocar(VillagerEntityMCA v, Mente m, long now) {
        List<Mente.Foco> c = new ArrayList<>(8);
        float[] d = m.impulsos;
        if (d[Mente.DUELO] > 0.15F && m.dueloPor != null) {
            c.add(foco(m, "duelo", "la pena por " + m.dueloPor.es(), "grief for " + m.dueloPor.en(), d[Mente.DUELO], now));
        }
        if (d[Mente.SEGURIDAD] > 0.35F) {
            boolean fresh = !m.amenaza.isEmpty() && now - m.amenazaT < 600;
            c.add(foco(m, "miedo", "el peligro" + (fresh ? " (" + m.amenaza + ")" : ""), "danger", d[Mente.SEGURIDAD], now));
        }
        if (d[Mente.DOLOR] > 0.5F) {
            c.add(foco(m, "dolor", "el golpe que me dieron", "the hit I took", 0.5F * d[Mente.DOLOR], now));
        }
        Episodio malo = null;
        Episodio bueno = null;
        float pm = 0;
        float pb = 0;
        for (int i = m.episodios.size() - 1, n = 0; i >= 0 && n < 48; i--, n++) {
            Episodio e = m.episodios.get(i);
            if (!e.vivo() || e.quien == null || !e.quien.persona() || now - e.t1 > 24000) {
                continue;
            }
            float p = e.saliencia * Math.abs(e.valencia) * e.confianza * (float) Math.exp(-(now - e.t1) / 12000.0);
            if (e.valencia < 0 && p > pm) {
                pm = p;
                malo = e;
            } else if (e.valencia > 0 && p > pb) {
                pb = p;
                bueno = e;
            }
        }
        if (malo != null && pm > 0.08F) {
            c.add(foco(m, "rencor:" + malo.quien.clave(), malo.narrar(true), malo.narrar(false), Math.min(1.0F, pm * 2.0F), now));
        }
        if (bueno != null && pb > 0.08F) {
            c.add(foco(m, "gratitud:" + bueno.quien.clave(), bueno.narrar(true), bueno.narrar(false), Math.min(1.0F, pb * 1.5F), now));
        }
        if (d[Mente.DEBER] > 0.5F) {
            String doing = Retina.memory(v).doing();
            boolean known = doing != null && !doing.isBlank();
            c.add(foco(m, "deber", known ? doing : "mi trabajo", known ? doing : "my work", d[Mente.DEBER] * 0.6F, now));
        }
        if (d[Mente.HAMBRE] > 0.5F) {
            c.add(foco(m, "hambre", "el hambre", "hunger", d[Mente.HAMBRE], now));
        }
        if (d[Mente.AFILIACION] > 0.6F) {
            c.add(foco(m, "soledad", "las ganas de hablar con alguien", "wanting company", d[Mente.AFILIACION] * 0.5F, now));
        }
        if (d[Mente.CANSANCIO] > 0.7F) {
            c.add(foco(m, "sueno", "el sueño", "sleepiness", d[Mente.CANSANCIO] * 0.6F, now));
        }
        c.sort(Comparator.comparingDouble((Mente.Foco f) -> puntaje(f, now)).reversed());
        m.foco.clear();
        m.foco.addAll(c.subList(0, Math.min(4, c.size())));
        Mente.Foco top = m.foco.isEmpty() ? null : m.foco.get(0);
        Mente.Foco cur = null;
        if (m.atencion != null) {
            for (Mente.Foco f : m.foco) {
                if (f.clave().equals(m.atencion.clave())) {
                    cur = f;
                }
            }
        }
        m.atencion = cur == null || top != null && top != cur && puntaje(top, now) > puntaje(cur, now) + 0.05F ? top : cur;
        TRAZAS.put(v.getUuid(), new Traza(v.getUuid(), v.getName().getString(), d[Mente.DUELO], m.dueloPor,
                m.atencion == null ? "" : m.atencion.es(), now));
        if (TRAZAS.size() > 1024) {
            TRAZAS.values().removeIf(t -> now - t.t() > 2400);
        }
    }

    private static Mente.Foco foco(Mente m, String clave, String es, String en, float peso, long now) {
        long desde = now;
        for (Mente.Foco f : m.foco) {
            if (f.clave().equals(clave)) {
                desde = f.desde();
                break;
            }
        }
        return new Mente.Foco(clave, es, en, peso, desde);
    }

    private static float puntaje(Mente.Foco f, long now) {
        return f.peso() - Math.min(0.5F, 0.1F * ((now - f.desde()) / 1200.0F));
    }

    // ------------------------------------------------------------------ bonds and hearts

    /** How much {@code h} cares for {@code o}, -1..1: hearts for a player, family ties for a villager, fear for a monster. */
    static float vinculo(ServerWorld world, VillagerEntityMCA h, Mente hm, @Nullable Ser o) {
        if (o == null || o.id() == null) {
            return 0;
        }
        if (o.clase() == Episodio.JUGADOR) {
            return vinculoJugador(h, o.id());
        }
        if (o.clase() == Episodio.BICHO) {
            return -0.5F;
        }
        if (o.clase() != Episodio.ALDEANO) {
            return 0;
        }
        if (o.id().equals(h.getUuid())) {
            return 1.0F;
        }
        FamilyTreeNode node = h.getRelationships().getFamilyEntry();
        if (o.id().equals(node.partner())) {
            return 1.0F;
        }
        if (o.id().equals(node.father()) || o.id().equals(node.mother()) || node.children().contains(o.id())) {
            return 0.9F;
        }
        if (node.siblings().contains(o.id())) {
            return 0.8F;
        }
        if (node.isRelative(o.id())) {
            return 0.6F;
        }
        return Mente.clamp(0.2F + (hm.trato(o.id()) - 0.5F), -1.0F, 0.7F);
    }

    static float vinculoJugador(VillagerEntityMCA h, UUID player) {
        Memories mem = h.getVillagerBrain().getMemories().get(player);
        return mem == null ? 0 : Mente.clamp(mem.getHearts() / 100.0F, -1.0F, 1.0F);
    }

    /**
     * Hearts moved by what {@code h} heard of a player: once per incident and player, never much for a rumour (one
     * teller, not sure), more when others or the player themselves confirm it; kept so they can be given back if it
     * turns out false. A player it has no memories of yet gets them when they next meet.
     */
    static void corazones(VillagerEntityMCA h, Mente hm, Episodio e, float bondVictima) {
        if (e.quien == null || e.quien.clase() != Episodio.JUGADOR || e.quien.id() == null || e.estado == Estado.FALSO) {
            return;
        }
        boolean malo = e.valencia < 0;
        if (malo && bondVictima < 0.3F) {
            return; // it does not care for the one it was done to
        }
        float magn = Math.min(1.0F, Math.abs(e.valencia) / 0.7F);
        int full = malo ? -Math.round(Math.min(15.0F, 12.0F * magn * e.confianza * (0.5F + bondVictima)))
                : Math.round(Math.min(4.0F, 4.0F * e.confianza * (0.5F + Math.max(0, bondVictima))));
        boolean rumor = e.modo == Modo.OIDO && e.apoyos < 2 && e.confianza < 0.9F;
        if (rumor) {
            full = malo ? Math.max(full, -5) : Math.min(full, 2);
        }
        String key = e.origen + ">" + e.quien.id();
        int done = hm.aplicados.getOrDefault(key, 0);
        int delta = full - done;
        if (malo ? delta >= 0 : delta <= 0) {
            return;
        }
        hm.aplicados.put(key, full);
        Mente.limitar(hm.aplicados);
        Memories mem = h.getVillagerBrain().getMemories().get(e.quien.id());
        if (mem != null) {
            mem.modHearts(delta);
        } else {
            hm.pendientes.merge(e.quien.id(), delta, Integer::sum);
            Mente.limitar(hm.pendientes);
        }
    }

    // ------------------------------------------------------------------ what it says of it

    /** For the chat's language models: the incidents that fit this talk, how it knows each, its beliefs, its feelings. */
    public static String forModel(VillagerEntityMCA v, PlayerEntity player) {
        if (!(v.getWorld() instanceof ServerWorld world)) {
            return "";
        }
        long now = world.getTime();
        Mente m = de(world, v);
        StringBuilder sb = new StringBuilder();
        List<Episodio> eps = m.recordar(now, player.getUuid(), null, 8);
        eps.removeIf(e -> e.saliencia < 0.2F);
        if (!eps.isEmpty()) {
            sb.append("Incidents $villager remembers (#id, what, how it knows it, when): ");
            for (Episodio e : eps) {
                sb.append('#').append(e.id).append(' ').append(e.narrar(true)).append(" (").append(e.como(true)).append(", ")
                        .append(Episodio.cuando(now - e.t1, true));
                if (e.estado == Estado.FALSO) {
                    sb.append(", resultó no ser cierto");
                } else if (e.modo == Modo.OIDO && e.apoyos < 2 && e.confianza < 0.9F) {
                    sb.append(", es un rumor: no estás seguro");
                }
                sb.append("); ");
            }
        }
        String creencias = creencias(m, now, 4);
        if (!creencias.isEmpty()) {
            sb.append("What $villager believes from them: ").append(creencias).append(". ");
        }
        String siente = sentimientos(m, true);
        if (!siente.isEmpty()) {
            sb.append("How $villager feels: ").append(siente).append(". ");
        }
        if (m.atencion != null) {
            sb.append("What holds its attention now: ").append(m.atencion.es()).append(". ");
        }
        sb.append("Its temperament: ").append(temperamento(m)).append(". ");
        if (!eps.isEmpty()) {
            sb.append("Speak of these as $villager knows them: what it lived or saw, for sure; a rumour, saying who told it and that it is not "
                    + "sure; never invent incidents, people or places that are not in this prompt. ");
        }
        return sb.length() > 1300 ? sb.substring(0, 1300) + "… " : sb.toString();
    }

    /** For its own inner voice ({@link net.mca.entity.ai.mente.Conciencia}): the incidents that weigh most, with their ids. */
    public static String paraPensar(VillagerEntityMCA v) {
        if (!(v.getWorld() instanceof ServerWorld world)) {
            return "";
        }
        long now = world.getTime();
        Mente m = de(world, v);
        StringBuilder sb = new StringBuilder();
        for (Episodio e : m.recordar(now, null, null, 5)) {
            if (e.saliencia < 0.25F) {
                continue;
            }
            sb.append('#').append(e.id).append(' ').append(e.narrar(true)).append(" (").append(e.como(true)).append(", ")
                    .append(Episodio.cuando(now - e.t1, true)).append("); ");
        }
        String siente = sentimientos(m, true);
        if (!siente.isEmpty()) {
            sb.append("siento: ").append(siente);
        }
        return sb.length() > 600 ? sb.substring(0, 600) + "…" : sb.toString().trim();
    }

    private static String creencias(Mente m, long now, int n) {
        StringBuilder sb = new StringBuilder();
        for (Hecho h : m.hechosFuertes(now, n)) {
            String s;
            if (h.clave.startsWith("trato:")) {
                float b = h.creencia();
                s = h.valor + (b > 0.6F ? " es buena gente" : b < 0.4F ? " no es de fiar" : " no sé qué pensar") + " (" + Math.round(b * 100) + "% seguro"
                        + (h.rumor() ? ", de oídas" : "") + ")";
            } else if (h.clave.startsWith("peligro:")) {
                s = "hay peligro: " + h.valor;
            } else {
                s = h.valor;
            }
            sb.append(sb.length() == 0 ? "" : "; ").append(s);
        }
        return sb.toString();
    }

    static String sentimientos(Mente m, boolean es) {
        float[] d = m.impulsos;
        List<String> out = new ArrayList<>(4);
        if (d[Mente.DUELO] > 0.15F && m.dueloPor != null) {
            out.add((es ? "con pena por " + m.dueloPor.es() : "grieving " + m.dueloPor.en()) + (d[Mente.DUELO] > 0.5F ? (es ? " (mucha)" : " (deeply)") : ""));
        }
        if (d[Mente.SEGURIDAD] > 0.4F) {
            out.add(es ? "con miedo" : "afraid");
        }
        if (d[Mente.DOLOR] > 0.5F) {
            out.add(es ? "adolorido por un golpe" : "hurting from a hit");
        }
        if (d[Mente.HAMBRE] > 0.6F) {
            out.add(es ? "con hambre" : "hungry");
        }
        if (d[Mente.CANSANCIO] > 0.7F) {
            out.add(es ? "con sueño" : "sleepy");
        }
        if (d[Mente.AFILIACION] > 0.7F) {
            out.add(es ? "con ganas de conversar" : "wanting company");
        }
        return String.join(", ", out);
    }

    static String temperamento(Mente m) {
        List<String> t = new ArrayList<>(5);
        t.add(m.afiliacion > 0.62F ? "cariñoso" : m.afiliacion < 0.38F ? "frío" : "cordial");
        t.add(m.riesgo > 0.62F ? "prudente" : m.riesgo < 0.38F ? "osado" : "sensato");
        t.add(m.credulidad > 0.62F ? "crédulo" : m.credulidad < 0.38F ? "desconfiado" : "razonable con los chismes");
        t.add(m.gregarismo > 0.62F ? "sociable" : m.gregarismo < 0.38F ? "solitario" : "a ratos sociable");
        t.add(m.neuroticismo > 0.62F ? "se afecta mucho" : m.neuroticismo < 0.38F ? "tranquilo" : "de ánimo parejo");
        return String.join(", ", t);
    }

    // ------------------------------------------------------------------ talking with a player

    /**
     * The player answered the question it asked about a rumour ("¿es verdad?"): a yes confirms it, a no is believed
     * when it trusts the player more than whoever told it (the rumour is then marked false and the hearts it cost
     * come back), else it stays wary. Null when the message is not such an answer. Server thread.
     */
    @Nullable
    public static String respuesta(VillagerEntityMCA v, ServerPlayerEntity player, String msg) {
        if (!(v.getWorld() instanceof ServerWorld world)) {
            return null;
        }
        try {
            long now = world.getTime();
            Mente m = de(world, v);
            m.ultimoSocial = now;
            if (m.preguntaA == null || !m.preguntaA.equals(player.getUuid()) || now > m.preguntaHasta) {
                return null;
            }
            String t = Acciones.normalize(msg).replaceAll("[^a-z ]", " ").replaceAll("\\s+", " ").trim();
            if (t.split(" ").length > 12 || t.contains("no se") || t.contains("no sé")) {
                return null;
            }
            boolean no = NIEGA.matcher(t).find();
            boolean si = !no && ADMITE.matcher(t).find();
            if (!no && !si) {
                m.preguntaA = null; // the talk moved on: a later "sí" is not about the rumour
                return null;
            }
            Episodio e = m.porOrigen(m.preguntaOrigen);
            m.preguntaA = null;
            if (e == null) {
                return null;
            }
            Boolean known = MenteLocal.playerSpanish(player.getUuid());
            boolean es = known != null ? known : !MenteLocal.clearlyNot(msg, true);
            String name = player.getName().getString();
            Hecho trato = m.hecho("trato:" + player.getUuid(), name, now);
            if (si) {
                e.confianza = 1.0F;
                e.apoyos = Math.max(e.apoyos, 3);
                trato.apoyar(e.valencia > 0, Math.abs(e.valencia), true, e.id, now);
                Ser victima = e.aQuien;
                corazones(v, m, e, victima == null ? 1.0F : Math.max(0.3F, vinculo(world, v, m, victima)));
                m.sucio(now);
                if (e.valencia < 0) {
                    return es ? "Ya veo. Al menos eres honesto, " + name + ", pero no me gustó nada." : "I see. At least you're honest, " + name + ", but I didn't like that.";
                }
                return es ? "¡Qué buena onda de tu parte, " + name + "!" : "That was kind of you, " + name + "!";
            }
            float creoJugador = 0.35F + 0.5F * vinculoJugador(v, player.getUuid()) + 0.3F * (m.credulidad - 0.5F);
            if (creoJugador >= e.confianza) {
                e.estado = Estado.FALSO;
                e.confianza *= 0.3F;
                Integer done = m.aplicados.remove(e.origen + ">" + player.getUuid());
                if (done != null && done != 0) {
                    v.getVillagerBrain().getMemoriesForPlayer(player).modHearts(-done);
                }
                if (e.digerido) {
                    trato.apoyar(e.valencia < 0, Math.abs(e.valencia) * 0.5F, false, e.id, now); // the weight the rumour had, taken back
                }
                m.sucio(now);
                return es ? "Bueno, te creo. Habrá sido un chisme no más." : "Alright, I believe you. Just gossip, then.";
            }
            e.confianza = Math.max(0.1F, e.confianza * 0.8F);
            m.sucio(now);
            return es ? "Mm... no sé si creerte, " + name + "." : "Hm... not sure I believe you, " + name + ".";
        } catch (RuntimeException ex) {
            falla("una respuesta", v, ex);
            return null;
        }
    }

    /** It said {@code line} in chat to {@code player}: a question about a rumour stays open only while it is the last thing said. */
    public static void dijo(VillagerEntityMCA v, UUID player, String line) {
        try {
            Mente m = VIVAS.get(v);
            if (m != null && m.preguntaA != null && m.preguntaA.equals(player) && !line.equals(m.preguntaLinea)) {
                m.preguntaA = null;
            }
        } catch (RuntimeException ignored) {
            // nothing to close
        }
    }

    /** Its own answers, without a model, to "¿estás enojado conmigo?", "¿qué pasó hoy?", "¿quién te pegó?", "¿por qué huiste?", "¿qué sabes de Rosa?". */
    @Nullable
    public static String responder(VillagerEntityMCA v, PlayerEntity player, String pregunta, String arg, boolean es) {
        if (!(v.getWorld() instanceof ServerWorld world)) {
            return null;
        }
        try {
            long now = world.getTime();
            Mente m = de(world, v);
            return switch (pregunta) {
                case "mad" -> animo(world, v, m, player, now, es);
                case "news" -> noticias(m, now, es);
                case "who" -> quien(m, arg, now, es);
                case "fled" -> huida(m, now, es);
                case "about" -> sobre(world, v, m, player, arg, now, es);
                default -> null;
            };
        } catch (RuntimeException ex) {
            falla("una pregunta", v, ex);
            return null;
        }
    }

    private static String animo(ServerWorld world, VillagerEntityMCA v, Mente m, PlayerEntity player, long now, boolean es) {
        UUID p = player.getUuid();
        Episodio malo = null;
        Episodio bueno = null;
        for (int i = m.episodios.size() - 1; i >= 0; i--) {
            Episodio e = m.episodios.get(i);
            if (!e.vivo() || e.quien == null || !e.quien.es(p) || now - e.t1 > 72000 || !e.tipo.contable) {
                continue;
            }
            if (e.valencia < 0 && malo == null) {
                malo = e;
            } else if (e.valencia > 0 && bueno == null) {
                bueno = e;
            }
        }
        float hearts = vinculoJugador(v, p);
        String pena = m.impulsos[Mente.DUELO] > 0.2F && m.dueloPor != null
                ? (es ? " Ando triste por " + m.dueloPor.es() + ", eso sí." : " I'm sad about " + m.dueloPor.en() + ", though.") : "";
        if (malo != null && (hearts < 0.1F || malo.saliencia > 0.5F)) {
            String como = malo.modo == Modo.OIDO ? (es ? " Al menos eso " + malo.como(true) + "." : " At least that's what " + (malo.fuente == null ? "I heard" : malo.fuente.en() + " told me") + ".") : "";
            return (es ? "Sí, un poco: " + malo.narrarATi(true) + " " + Episodio.cuando(now - malo.t1, true) + "."
                    : "Yes, a bit: " + malo.narrarATi(false) + " " + Episodio.cuando(now - malo.t1, false) + ".") + como + pena;
        }
        if (bueno != null) {
            return (es ? "No, para nada. " + mayuscula(bueno.narrarATi(true)) + " " + Episodio.cuando(now - bueno.t1, true) + ", no se me olvida."
                    : "No, not at all. " + mayuscula(bueno.narrarATi(false)) + " " + Episodio.cuando(now - bueno.t1, false) + ", I won't forget it.") + pena;
        }
        if (!pena.isEmpty()) {
            return (es ? "Contigo no." : "Not with you.") + pena;
        }
        return hearts >= 0 ? (es ? "No, estoy bien contigo." : "No, we're fine.") : (es ? "Todavía no te tengo mucha confianza." : "I don't quite trust you yet.");
    }

    private static String noticias(Mente m, long now, boolean es) {
        List<String> out = new ArrayList<>(3);
        for (Episodio e : m.recordar(now, null, null, 12)) {
            if (now - e.t1 > 24000 || e.saliencia < 0.3F || e.estado == Estado.FALSO || e.tipo == Tipo.REFLEXION) {
                continue;
            }
            out.add(e.narrar(es) + " (" + e.como(es) + ")");
            if (out.size() >= 3) {
                break;
            }
        }
        if (out.isEmpty()) {
            return es ? "Nada nuevo por aquí, la verdad." : "Nothing new around here, really.";
        }
        return (es ? "Lo de hoy: " : "Today: ") + String.join("; ", out) + ".";
    }

    private static String quien(Mente m, String text, long now, boolean es) {
        String t = Acciones.normalize(text);
        Tipo tipo = t.contains("rob") || t.contains("stole") ? Tipo.ROBO : t.contains("mat") || t.contains("kill") ? Tipo.MUERTE
                : t.contains("regal") || t.contains("gave") || t.contains(" dio") ? Tipo.REGALO : Tipo.HERIDO;
        Episodio e = m.ultimo(tipo, null);
        if (tipo == Tipo.HERIDO && (e == null || e.aQuien != null)) {
            e = null;
            for (int i = m.episodios.size() - 1; i >= 0 && e == null; i--) {
                Episodio o = m.episodios.get(i);
                if (o.tipo == Tipo.HERIDO && o.aQuien == null && o.vivo()) {
                    e = o;
                }
            }
        }
        if (e == null || e.quien == null) {
            return switch (tipo) {
                case ROBO -> es ? "Que yo sepa, nadie me ha robado." : "Nobody has stolen from me, as far as I know.";
                case MUERTE -> es ? "No sé de nadie que haya matado a alguien." : "I don't know of anyone killing anyone.";
                case REGALO -> es ? "Nadie me ha regalado nada últimamente." : "Nobody has given me anything lately.";
                default -> es ? "Nadie me ha pegado últimamente." : "Nobody has hit me lately.";
            };
        }
        return (es ? "Fue " + e.quien.es() + ": " + e.narrar(true) + ", " + Episodio.cuando(now - e.t1, true) + " (" + e.como(true) + ")."
                : "It was " + e.quien.en() + ": " + e.narrar(false) + ", " + Episodio.cuando(now - e.t1, false) + " (" + e.como(false) + ").");
    }

    private static String huida(Mente m, long now, boolean es) {
        Episodio e = m.ultimo(Tipo.HUI, null);
        if (e == null || now - e.t1 > 48000) {
            return es ? "No he tenido que huir de nada últimamente." : "I haven't had to run from anything lately.";
        }
        return mayuscula(e.narrar(es)) + ", " + Episodio.cuando(now - e.t1, es) + ".";
    }

    private static String sobre(ServerWorld world, VillagerEntityMCA v, Mente m, PlayerEntity player, String arg, long now, boolean es) {
        String a = Acciones.normalize(arg).replaceAll("[^a-z0-9 ]", " ").trim();
        String first = a.isEmpty() ? "" : a.split("\\s+")[0];
        Ser who = null;
        if (first.equals("mi") || first.equals("me") || first.equals("ti") || first.isEmpty()) {
            who = Ser.de(player);
        } else {
            for (int i = m.episodios.size() - 1; i >= 0 && who == null; i--) {
                Episodio e = m.episodios.get(i);
                for (Ser s : new Ser[]{e.quien, e.aQuien, e.fuente}) {
                    if (s != null && s.persona() && Acciones.normalize(s.es()).split("\\s+")[0].equals(first)) {
                        who = s;
                        break;
                    }
                }
            }
        }
        if (who == null || who.id() == null) {
            return es ? "No sé nada de " + (arg.isBlank() ? "eso" : arg.trim()) + "." : "I don't know anything about " + (arg.isBlank() ? "that" : arg.trim()) + ".";
        }
        boolean tu = who.es(player.getUuid());
        Hecho h = m.hechos.get("trato:" + who.id());
        float b = h == null ? 0.5F : h.creencia();
        String juicio;
        if (who.clase() == Episodio.ALDEANO && vinculo(world, v, m, who) >= 0.6F) {
            juicio = es ? (tu ? "Te quiero mucho" : who.es() + " es de mi familia") : (tu ? "I love you" : who.en() + " is family");
        } else if (h == null) {
            juicio = es ? (tu ? "Todavía no sé bien qué pensar de ti" : "No sé bien qué pensar de " + who.es()) : (tu ? "I'm not sure what to think of you yet" : "I'm not sure what to think of " + who.en());
        } else {
            juicio = es ? (tu ? (b > 0.6F ? "Me caes bien" : b < 0.4F ? "No me das mucha confianza" : "No sé qué pensar de ti")
                    : who.es() + (b > 0.6F ? " es buena gente" : b < 0.4F ? " no es de fiar" : ", no sé qué pensar"))
                    : (tu ? (b > 0.6F ? "I like you" : b < 0.4F ? "I don't really trust you" : "I'm not sure about you")
                    : who.en() + (b > 0.6F ? " is good people" : b < 0.4F ? " can't be trusted" : ", I'm not sure"));
            if (h.rumor()) {
                juicio += es ? ", aunque es de oídas" : ", though it's hearsay";
            }
        }
        List<String> hechos = new ArrayList<>(2);
        for (Episodio e : m.recordar(now, who.id(), null, 6)) {
            if (!e.sobre(who.id()) || e.tipo == Tipo.DIJE || e.estado == Estado.FALSO) {
                continue;
            }
            hechos.add((tu && e.quien != null && e.quien.es(player.getUuid()) ? e.narrarATi(es) : e.narrar(es)) + " (" + e.como(es) + ", "
                    + Episodio.cuando(now - e.t1, es) + ")");
            if (hechos.size() >= 2) {
                break;
            }
        }
        return juicio + "." + (hechos.isEmpty() ? "" : " " + mayuscula(String.join("; ", hechos)) + ".");
    }

    static String mayuscula(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------------ for the command

    /** Everything about one mind, for /mca-nucleo. */
    public static List<String> informe(ServerWorld world, VillagerEntityMCA v) {
        long now = world.getTime();
        Mente m = de(world, v);
        List<String> out = new ArrayList<>();
        out.add(v.getName().getString() + " — " + temperamento(m) + String.format(Locale.ROOT,
                " (afiliación %.2f, riesgo %.2f, credulidad %.2f, gregarismo %.2f, neuroticismo %.2f)",
                m.afiliacion, m.riesgo, m.credulidad, m.gregarismo, m.neuroticismo));
        StringBuilder d = new StringBuilder("Impulsos:");
        for (int i = 0; i < m.impulsos.length; i++) {
            d.append(' ').append(Mente.IMPULSOS_ES[i]).append(String.format(Locale.ROOT, " %.2f", m.impulsos[i]));
        }
        out.add(d.toString());
        StringBuilder f = new StringBuilder("Foco:");
        for (Mente.Foco x : m.foco) {
            f.append(x == m.atencion ? " [" : " ").append(x.es()).append(String.format(Locale.ROOT, " %.2f", puntaje(x, now))).append(x == m.atencion ? "]" : "");
        }
        out.add(m.foco.isEmpty() ? "Foco: nada en particular" : f.toString());
        out.add("Episodios (" + m.episodios.size() + ", los que más pesan):");
        for (Episodio e : m.recordar(now, null, null, 10)) {
            out.add(String.format(Locale.ROOT, " #%d %s — %s, %s · sal %.2f conf %.2f %s%s", e.id, e.narrar(true), e.como(true),
                    Episodio.cuando(now - e.t1, true), e.saliencia, e.confianza, e.estado.name().toLowerCase(Locale.ROOT),
                    e.causas.length > 0 ? " · por #" + java.util.Arrays.toString(e.causas) : ""));
        }
        List<Hecho> hs = m.hechosFuertes(now, 6);
        if (!hs.isEmpty()) {
            out.add("Creencias:");
            for (Hecho h : hs) {
                out.add(String.format(Locale.ROOT, " %s → %s · a favor %.1f en contra %.1f (%d visto%s)%s", h.clave.replaceAll(":[0-9a-f-]{36}", ""), h.valor,
                        h.pos, h.neg, h.directos, h.directos == 1 ? "" : "s", h.rumor() ? " · rumor" : ""));
            }
        }
        if (!m.pendientes.isEmpty()) {
            out.add("Corazones pendientes: " + m.pendientes.size() + " jugador(es)");
        }
        return out;
    }
}
