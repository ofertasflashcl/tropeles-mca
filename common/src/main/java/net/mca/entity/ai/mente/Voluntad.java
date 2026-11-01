package net.mca.entity.ai.mente;

import net.mca.entity.ai.body.Mining;
import net.mca.MCA;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Chore;
import net.mca.entity.ai.body.CombatMind;
import net.mca.entity.ai.body.Hands;
import net.mca.entity.ai.body.Motor;
import net.mca.entity.ai.body.Postura;
import net.mca.entity.ai.chatAI.Acciones;
import net.mca.entity.ai.brain.tasks.FollowTask;
import net.mca.entity.ai.brain.tasks.chore.ProspectingTask;
import net.mca.entity.ai.sense.Entorno;
import net.mca.entity.ai.sense.Mirada;
import net.mca.entity.ai.sense.Retina;
import net.mca.entity.ai.sense.VisualMemory;
import net.mca.entity.ai.obra.Constructor;
import net.mca.entity.ai.obra.Obra;
import net.mca.entity.ai.obra.ObrasMundo;
import net.mca.entity.ai.obra.Plano;
import net.mca.entity.ai.obra.Proyeccion;
import net.mca.entity.ai.obra.Sitio;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

/**
 * The will of a connected villager: what it has made up its mind to do, the way a player decides between their own
 * plans and a friend's call. {@link ProspectingTask} asks it first on every tick; while the will holds the body the
 * task's own life (gear, wood, the mine, food, home, the chest) waits where it was, and picks up again from the same
 * place when the will lets go, its mine trail and its plans kept.
 * <ul>
 * <li>{@link Modo#LIBRE}: its own life, the task drives.</li>
 * <li>{@link Modo#SIGUIENDO}: at a player's side like a companion ({@link FollowTask#guard}: it follows, digs or
 * builds its way to them, fights what threatens them), eyes working, eating from its bag.</li>
 * <li>{@link Modo#ALERTA}: "¡cuidado!", "¡para!": it stops dead, turns to the player and asks what is going on; in
 * the rock, or with a monster seen close by lately, it crouches and keeps its voice down. It defends itself from
 * whatever comes within reach and goes back to what it was doing when told it was nothing, or after
 * {@value #ALERT_SILENCE} ticks of silence.</li>
 * <li>{@link Modo#ESPERANDO}: "quédate aquí": it waits on the spot, looks around and at the player, defends itself.</li>
 * <li>{@link Modo#ENCARGO}: "tala un poco", "cosecha", "caza algo", "pesca", "hazme un cofre": it does what it was asked
 * with its own hands ({@link Encargo}), never MCA's chore (that would end the connection), then goes back to what it was
 * doing for them; what it got it hands over when it was asked to bring it, otherwise it offers it at their side.</li>
 * </ul>
 * The mode is kept in the villager (a relog keeps it), except the alert, which falls back to the mode under it. The
 * chat changes it ({@link net.mca.entity.ai.chatAI.TriggerCommandInfos}); every change is logged. What it thinks
 * ({@link #pensando()}) is told to the language models and shown by {@code /mca-ojo}. Server thread only.
 */
public final class Voluntad {
    public enum Modo {
        LIBRE("libre", "free, living your own life (your own plans: gear, wood, mining, food, home)"),
        SIGUIENDO("siguiendo", "following $player as a companion and guarding them"),
        ALERTA("alerta", "on alert: you stopped dead and wait to hear what is going on"),
        ESPERANDO("esperando", "waiting right here as you were asked"),
        CONOCIENDO("conociendo", "meeting $player: you just looked them over from head to toe, said hello and asked their name, and you"
                + " take in the place while they answer"),
        ENCARGO("encargo", "doing what $player asked of you, with your own hands"),
        OBRA("obra", "building a house for $player with your own hands: you find, chop, dig, craft, smelt and set every block yourself,"
                + " following the plan they can see as a see-through ghost");

        private final String palabra;
        private final String english;

        Modo(String palabra, String english) {
            this.palabra = palabra;
            this.english = english;
        }

        public String palabra() {
            return palabra;
        }

        public String english() {
            return english;
        }
    }

    /** Ticks of silence after which an alert is over: it carries on, and says so. */
    public static final int ALERT_SILENCE = 800;
    /** Ticks without the player it follows or waits for before it goes back to its own life. */
    public static final int LOST_PLAYER = 6000;
    /** A villager whose will ran this recently counts as connected (the checks run the task without a job). */
    public static final int CONNECTED_GRACE = 40;
    /** How close a monster must come before an alert or a wait turns into a fight. */
    public static final double DEFEND_ALERT = 3.5;
    public static final double DEFEND_WAIT = 6.0;
    /** A monster seen this close lately is a reason to crouch on an alert. */
    public static final double MONSTER_NEAR = 16.0;
    public static final int MONSTER_RECENT = 400;
    private static final String CROUCH = "cuidado";

    private static final Map<VillagerEntityMCA, Voluntad> ALL = new WeakHashMap<>();

    private Modo modo = Modo.LIBRE;
    /** The mode an alert interrupted, and its player and spot: what it goes back to. */
    private Modo debajo = Modo.LIBRE;
    @Nullable
    private UUID debajoQuien;
    @Nullable
    private BlockPos debajoEspera;
    @Nullable
    private UUID quien;
    private WeakReference<PlayerEntity> quienRef = new WeakReference<>(null);
    private String quienNombre = "";
    @Nullable
    private BlockPos espera;
    private long desde;
    /** The last time its player said anything to it. */
    private long oido;
    private long ultimoTick = Long.MIN_VALUE / 2;
    /** Since when the player it is with has been nowhere to be found, or -1. */
    private long sinJugador = -1;
    /** The will held the body on its last tick (the task let go of it). */
    private boolean tiene;
    /** It asked {@link Postura} for the crouch, for caution. */
    private boolean agachado;
    /** Why it crouched, in its words. */
    private String porQue = "";
    private boolean es = true;
    private boolean esConocido;
    private String pensando = "";
    @Nullable
    private LivingEntity enemigo;
    private long nextSweep;
    private long lookUntil;
    private boolean loggedError;
    private final FollowTask follow = new FollowTask();
    private final Mirada mirada = new Mirada();
    final Iniciativa iniciativa = new Iniciativa();
    /** Its inner voice: a thought now and then, lessons, its own changes of plans ({@link Conciencia}). */
    private final Conciencia conciencia = new Conciencia();
    private final Atasco atasco = new Atasco();
    /** The player it went to find food away from, to go back to once it has eaten. */
    @Nullable
    private UUID volver;
    private long volverDesde;
    /** The errand it is on, or null; what it goes back to after it: the mode, its player and spot. */
    @Nullable
    private Encargo encargo;
    private Modo tras = Modo.LIBRE;
    @Nullable
    private UUID trasQuien;
    @Nullable
    private BlockPos trasEspera;
    /** What it got for someone, to hand over (or offer) once it is at their side. */
    @Nullable
    private Entrega entrega;
    /** What it offered and to whom, waiting for a yes or a no, until {@link #ofertaHasta}. */
    @Nullable
    private Entrega oferta;
    private long ofertaHasta;
    /** Its own words for the last errand order it took (Spanish, English), what they were about, and when (real time). */
    private String dichoEs = "";
    private String dichoEn = "";
    private String dichoClave = "";
    private long dichoMs;
    /** It leaves its house by itself (hunger, the night): no pause the player set, it goes back to it on its own. */
    private boolean pausaInterna;
    private long llamadoObra = Long.MIN_VALUE / 2;
    /** What it goes back to once the house is done (an errand in the middle of it has its own {@link #tras}). */
    private Modo obraTras = Modo.LIBRE;
    @Nullable
    private UUID obraTrasQuien;
    @Nullable
    private BlockPos obraTrasEspera;
    /** "Aquí", "ahí", "here": the house goes where the player is looking. */
    private static final Pattern AQUI = Pattern.compile("(?<![a-z])(aqui|aquí|aca|acá|ahi|ahí|alli|allí|alla|allá|here|there|este sitio|ese sitio|"
            + "donde miro|donde estoy|donde apunto|right here)(?![a-z])");

    /** Things for a player: what, for whom, whether it hands them over by itself, and what it says then. */
    private record Entrega(UUID para, Map<Item, Integer> cosas, boolean auto, String es, String en) {
    }

    /** "Sí", "dale", "pásamelos": yes to what it offered (a short answer). */
    private static final Pattern SI = Pattern.compile("^(si|sii+|sip|sep|dale|claro|obvio|ok|okay|okey|vale|bueno|porfa|por favor|pasamelo|pasamelos|"
            + "pasamela|pasamelas|damelo|damelos|damela|damelas|dame|tiramelo|tiramelos|yes|yeah|yep|sure|please|give)(?![a-z])[^?]*$");
    /** "No", "quédatelo": no thanks. */
    private static final Pattern NO_GRACIAS = Pattern.compile("^(no|nop|nah|nel|quedatelo|quedatelos|quedatela|quedatelas|guardalo|guardalos|"
            + "no hace falta|keep (it|them)|nope)(?![a-z])[^?]*$");

    private Voluntad() {
    }

    /** The will of this villager, as saved in it. */
    public static Voluntad of(VillagerEntityMCA villager) {
        return ALL.computeIfAbsent(villager, v -> {
            Voluntad w = new Voluntad();
            w.load(v);
            return w;
        });
    }

    /** Connected: its job is to live on its own ({@link Chore#PROSPECT}), or its will ran a moment ago. */
    public static boolean conectado(VillagerEntityMCA villager) {
        if (villager.getVillagerBrain().getCurrentJob() == Chore.PROSPECT) {
            return true;
        }
        Voluntad w = ALL.get(villager);
        return w != null && villager.getWorld().getTime() - w.ultimoTick <= CONNECTED_GRACE;
    }

    /**
     * Connected from its menu by {@code player}: before anything else it meets them, the way a person would
     * ({@link Companero}): a look from head to toe, a hello and their name; then it goes along at their side.
     */
    public static void conectar(VillagerEntityMCA villager, PlayerEntity player) {
        Voluntad w = of(villager);
        Companero.empezar(villager, player);
        w.change(villager, Modo.CONOCIENDO, player, null, player.getName().getString() + " me conectó: lo conozco");
    }

    /** The connection ended (the job was dropped or changed): back to nothing, standing up, nothing saved. */
    public static void olvidar(VillagerEntityMCA villager) {
        Voluntad w = ALL.remove(villager);
        if (w != null) {
            w.leave(villager, w.modo, Modo.LIBRE);
        }
        if (!villager.getVillagerBrain().getWill().isEmpty()) {
            villager.getVillagerBrain().setWill("");
        }
    }

    // ------------------------------------------------------------------ what the chat may ask

    public Modo modo() {
        return modo;
    }

    /** The mode it goes back to after an alert (its own mode otherwise). */
    public Modo debajo() {
        return modo == Modo.ALERTA ? debajo : modo;
    }

    /** Its player right now (following, waiting for, alerted by), or null. */
    @Nullable
    public UUID quien() {
        return quien;
    }

    public boolean agachado() {
        return agachado;
    }

    /** Its sense of getting nowhere ({@link Atasco}): the task asks it every tick of its own life. */
    public Atasco atasco() {
        return atasco;
    }

    /**
     * It tells the player it is with (or the one who connected it) what it is doing and why, when it changes its plans
     * for a reason: "No me acuerdo de otra salida, cavo una escalera nueva." At most one such line every few seconds;
     * every one is also a memory ({@link Recuerdos}). Server thread; never throws.
     */
    public static void contar(VillagerEntityMCA villager, String es, String en) {
        try {
            if (!(villager.getWorld() instanceof ServerWorld world)) {
                return;
            }
            Voluntad w = of(villager);
            String line = w.spanish() ? es : en;
            Recuerdos.anotar(villager, "plan", es, Mining.pies(villager));
            w.iniciativa.contar(world, villager, w, line, world.getTime());
        } catch (RuntimeException e) {
            MCA.LOGGER.warn("[Tropeles] {} no pudo contar lo que hace: {}", villager.getName().getString(), e.toString());
        }
    }

    /**
     * As {@link #contar(VillagerEntityMCA, String, String)}, with the real reason behind the change ({@code porque}, in
     * Spanish: what it tried, what it found), kept in its memory of the decision so it can tell the truth when asked
     * "¿por qué...?". Server thread; never throws.
     */
    public static void contar(VillagerEntityMCA villager, String es, String en, @Nullable String porque) {
        if (porque == null || porque.isBlank()) {
            contar(villager, es, en);
            return;
        }
        try {
            if (!(villager.getWorld() instanceof ServerWorld world)) {
                return;
            }
            Voluntad w = of(villager);
            Recuerdos.anotar(villager, "plan", es + " (porque " + porque.trim() + ")", Mining.pies(villager));
            w.iniciativa.contar(world, villager, w, w.spanish() ? es : en, world.getTime());
        } catch (RuntimeException e) {
            MCA.LOGGER.warn("[Tropeles] {} no pudo contar lo que hace: {}", villager.getName().getString(), e.toString());
        }
    }

    /** Its current mode, goal and why, in a short line in Spanish. */
    public String pensando() {
        return pensando;
    }

    /** For /mca-ojo and the log: "siguiendo — sigo a Benja (a 3 bloques)". */
    public static String resumen(VillagerEntityMCA villager) {
        if (!conectado(villager)) {
            return "no está conectado";
        }
        Voluntad w = of(villager);
        return w.modo.palabra() + (w.modo == Modo.ALERTA ? " (luego " + w.debajo.palabra() + ")" : "") + " — " + w.pensando;
    }

    /** For a language model: its mode and what it is thinking. */
    public static String forModel(VillagerEntityMCA villager, String player) {
        Voluntad w = of(villager);
        String mode = w.modo.english().replace("$player", w.quienNombre.isEmpty() ? player : w.quienNombre);
        if (w.modo == Modo.ALERTA) {
            mode += "; afterwards you go back to " + (w.debajo == Modo.LIBRE ? "your own life" : w.debajo.english().replace("$player", w.quienNombre));
        }
        if (w.encargo != null) {
            mode += ": " + w.encargo.english() + "; after it you go back to " + (w.tras == Modo.SIGUIENDO ? "their side" : w.tras == Modo.ESPERANDO
                    ? "wait where you were" : "your own life");
        }
        if (villager.getWorld() instanceof ServerWorld sw && (w.modo == Modo.OBRA || w.modo == Modo.ALERTA && w.debajo == Modo.OBRA
                || w.modo == Modo.LIBRE)) {
            Obra o = ObrasMundo.get(sw).de(villager.getUuid());
            if (o != null) {
                mode += ". The house you are building for " + (o.paraNombre.isEmpty() ? "someone" : o.paraNombre) + (o.pausaJugador ? " (paused: they told you"
                        + " to do something else; \"sigue con la casa\" resumes it)" : "") + ": " + Constructor.estado(sw, villager, o, false);
            }
        }
        if (w.oferta != null && villager.getWorld().getTime() <= w.ofertaHasta) {
            mode += ". You just offered " + Encargo.palabras(w.oferta.cosas(), false) + " to " + player + ": a yes from them means hand it over";
        }
        return mode + ". Your thoughts right now (in Spanish): " + w.pensando;
    }

    /** Someone said something to it (server thread): an alert is still being talked about, its language noted. */
    public static void oyo(VillagerEntityMCA villager, PlayerEntity player, boolean spanish) {
        Voluntad w = of(villager);
        w.es = spanish;
        w.esConocido = true;
        if (w.quien == null || w.quien.equals(player.getUuid())) {
            w.oido = villager.getWorld().getTime();
        }
    }

    /** The language it last heard, or the game's when it heard none. */
    public boolean spanish() {
        return esConocido ? es : !(MCA.language != null && MCA.language.startsWith("en"));
    }

    // ------------------------------------------------------------------ orders from the chat, each with its account

    /** "Sígueme": at the player's side, still connected; its job and its mine wait. */
    public static String seguir(VillagerEntityMCA villager, PlayerEntity player, boolean spanish) {
        Voluntad w = of(villager);
        oyo(villager, player, spanish);
        w.change(villager, Modo.SIGUIENDO, player, null, player.getName().getString() + " me pidió que lo siga");
        return "You are following " + player.getName().getString() + " now, at their side, fighting whatever threatens them. You are still "
                + "living your own life: your mine, your chest and your plans wait until you are told to go back to them.";
    }

    /** "Quédate aquí", "espérame": it waits on this spot. */
    public static String esperar(VillagerEntityMCA villager, PlayerEntity player, boolean spanish) {
        Voluntad w = of(villager);
        oyo(villager, player, spanish);
        w.change(villager, Modo.ESPERANDO, player, Mining.pies(villager).toImmutable(), player.getName().getString() + " me pidió que lo espere aquí");
        return "You stay right here and wait for " + player.getName().getString() + ", looking around and keeping yourself safe, until they call you "
                + "or send you back to your own things.";
    }

    /** "Haz lo tuyo": back to its own life, where it left it. */
    public static String libre(VillagerEntityMCA villager, @Nullable PlayerEntity player, boolean spanish) {
        Voluntad w = of(villager);
        if (player != null) {
            oyo(villager, player, spanish);
        }
        w.volver = null;
        w.change(villager, Modo.LIBRE, null, null, player == null ? "vuelvo a lo mío" : player.getName().getString() + " me dijo que vuelva a lo mío");
        return "You went back to your own life, right where you left it: your own plans, your gear, your mine.";
    }

    /**
     * "¡Cuidado!", "¡para!": it stops at once, turns to the player, crouches when it is in the rock or saw a monster
     * close by lately, and waits for an explanation.
     */
    public static String alerta(VillagerEntityMCA villager, PlayerEntity player, boolean spanish) {
        Voluntad w = of(villager);
        oyo(villager, player, spanish);
        String name = player.getName().getString();
        w.change(villager, Modo.ALERTA, player, null, name + " me avisó que tenga cuidado");
        w.oido = villager.getWorld().getTime();
        Motor.standStill(villager);
        villager.getBrain().forget(MemoryModuleType.WALK_TARGET);
        w.face(villager, player);
        w.judgeCaution(villager);
        w.think(villager);
        String crouch = w.agachado ? ", crouched and keeping your voice down because " + (w.porQue.startsWith("bajo") ? "you are underground" : "you saw a monster nearby") : "";
        return "You stopped dead at once and turned to face " + name + crouch + ". You stay still and ask " + name
                + " what is happening, in a short question.";
    }

    /** "Tranquilo, sigue", "falsa alarma": the alert is over, back to the mode it interrupted. Null when not on alert. */
    @Nullable
    public static String seguirComoAntes(VillagerEntityMCA villager, @Nullable PlayerEntity player, boolean spanish) {
        Voluntad w = of(villager);
        if (w.modo != Modo.ALERTA) {
            return null;
        }
        if (player != null) {
            oyo(villager, player, spanish);
        }
        Modo back = w.resume(villager, player == null ? "pasó la alerta" : player.getName().getString() + " me dijo que no era nada");
        return "It was nothing: you stand up and carry on " + switch (back) {
            case SIGUIENDO -> "following " + w.quienNombre;
            case ESPERANDO -> "waiting where you were";
            default -> "with your own plans";
        } + ".";
    }

    /** The question it asks when stopped by an alert, in its own words: quiet when it crouches. */
    public static String pregunta(VillagerEntityMCA villager, boolean spanish) {
        Voluntad w = ALL.get(villager);
        boolean quiet = w != null && w.agachado;
        String[] lines = spanish
                ? quiet ? new String[]{"Shh... ¿qué pasa?", "¿Qué viste?", "¿Hay algo por ahí?", "¿Qué fue eso?"}
                : new String[]{"¿Qué pasa?", "¿Qué viste?", "¿Hay algo?", "¿Qué ocurre?"}
                : quiet ? new String[]{"Shh... what is it?", "What did you see?", "Is something there?"}
                : new String[]{"What's up?", "What did you see?", "Is something there?", "What is it?"};
        return lines[villager.getRandom().nextInt(lines.length)];
    }

    /** What it says on carrying on after an alert. */
    public static String sigoLinea(VillagerEntityMCA villager, boolean spanish) {
        Modo m = of(villager).modo;
        return switch (m) {
            case SIGUIENDO -> spanish ? "Vale, sigo contigo." : "Okay, I'm right behind you.";
            case ESPERANDO -> spanish ? "Bueno, sigo esperando aquí." : "Alright, I'll keep waiting here.";
            default -> spanish ? "Bueno, sigo con lo mío." : "Alright, back to what I was doing.";
        };
    }

    // ------------------------------------------------------------------ the body

    /**
     * One tick, called by {@link ProspectingTask} before anything else, never in the middle of one of its jumps. True
     * when the will has the body this tick (the task does only its metabolism then); false in its own life. The first
     * tick it takes the body the task drops its half-made step ({@link ProspectingTask#soltar}); the first tick it
     * gives it back the task plans again from where it stands ({@link ProspectingTask#retomar}).
     */
    public boolean manda(ServerWorld world, VillagerEntityMCA villager, @Nullable ProspectingTask task) {
        long now = world.getTime();
        ultimoTick = now;
        boolean drive = modo != Modo.LIBRE;
        if (drive && !tiene) {
            tiene = true;
            if (task != null) {
                task.soltar(world, villager);
            }
        } else if (!drive && tiene) {
            tiene = false;
            if (task != null) {
                task.retomar(world, villager);
            }
        }
        try {
            switch (modo) {
                case SIGUIENDO -> {
                    Companero.Pide hunger = task == null ? Companero.Pide.NADA : hunger(world, villager, task);
                    if (hunger == Companero.Pide.BUSCAR) {
                        findFood(world, villager, task);
                    } else if (hunger == Companero.Pide.NADA) {
                        followTick(world, villager);
                        followStuck(world, villager);
                    }
                }
                case CONOCIENDO -> meetTick(world, villager);
                case ENCARGO -> encargoTick(world, villager, task);
                case OBRA -> obraTick(world, villager, task);
                case ALERTA -> alertTick(world, villager);
                case ESPERANDO -> waitTick(world, villager);
                default -> backTick(world, villager, task);
            }
            if ((villager.age % 20) == 0) {
                think(villager);
            }
            iniciativa.tick(world, villager, this, task);
            conciencia.tick(world, villager, this, task);
        } catch (RuntimeException e) {
            // the will must never stop the task's own life
            if (!loggedError) {
                loggedError = true;
                MCA.LOGGER.error("[Tropeles] la voluntad de {} falló", villager.getName().getString(), e);
            }
        }
        return drive;
    }

    private void followTick(ServerWorld world, VillagerEntityMCA villager) {
        PlayerEntity player = player(world);
        if (player == null) {
            missing(world, villager);
            return;
        }
        sinJugador = -1;
        follow.guard(world, villager, player);
        if ((villager.age % 20) == 0) {
            Companero.juntos(world, villager, player);
        }
        if (entrega != null && player.getUuid().equals(entrega.para()) && (villager.age % 5) == 0
                && villager.squaredDistanceTo(player) < 3.2 * 3.2 && Retina.canSee(world, villager, player)) {
            alLado(world, villager, player);
        }
    }

    /** At the side of the one it got things for: it hands them over (asked to bring them) or offers them. */
    private void alLado(ServerWorld world, VillagerEntityMCA villager, PlayerEntity player) {
        Entrega en = entrega;
        entrega = null;
        String n = Companero.nombre(villager, player);
        if (!en.auto() || !(player instanceof ServerPlayerEntity sp)) {
            hablar(world, villager, player, en.es(), en.en());
            oferta = en;
            ofertaHasta = world.getTime() + 2400;
            return;
        }
        Map<Item, Integer> gave = entregar(world, villager, sp, en.cosas());
        if (gave.isEmpty()) {
            hablar(world, villager, player, "Uy, " + n + ", ya no tengo lo que te traía.", "Oops, " + n + ", I don't have what I was bringing you anymore.");
        } else {
            hablar(world, villager, player, "Toma, " + n + ": " + Encargo.palabras(gave, true) + ".", "Here you go, " + n + ": " + Encargo.palabras(gave, false) + ".");
        }
        if (tras != Modo.SIGUIENDO || !player.getUuid().equals(trasQuien)) {
            volverTras(world, villager, "ya le di a " + n + " lo que le traía");
        } else {
            tras = Modo.LIBRE;
            trasQuien = null;
            trasEspera = null;
        }
    }

    // ------------------------------------------------------------------ errands ({@link Encargo})

    /**
     * "Tala un poco", "tráeme 10 troncos", "cosecha", "caza una vaca", "pesca", "hazme un cofre": an errand for
     * {@code player}, done with its own hands and still connected, never MCA's chore. It checks first that it can (no
     * trees down here, no rod nor string, three iron short) and says why not; otherwise it sets off, and once done it
     * goes back to what it was doing for them. The account for a language model, in plain English; its own words for
     * the order in {@link #dicho}.
     */
    public static String encargar(VillagerEntityMCA villager, PlayerEntity player, Encargo.Tipo tipo, String words, boolean spanish) {
        Voluntad w = of(villager);
        oyo(villager, player, spanish);
        String name = Companero.nombre(villager, player);
        if (!(villager.getWorld() instanceof ServerWorld world)) {
            return "You have no way to do that right now.";
        }
        ProspectingTask task = ProspectingTask.of(villager);
        Encargo e = Encargo.leer(tipo, words, villager, player, name);
        if (e == null) {
            w.dicho(tipo.name(), "Eso no sé hacerlo todavía, " + name + ".", "I don't know how to make that yet, " + name + ".");
            return "You have no idea how to make that: it is nothing you know how to craft. Say so, in a few words.";
        }
        String[] no = task.noPuede(world, villager, e);
        if (no != null) {
            w.dicho(tipo.name(), no[0], no[1]);
            MCA.LOGGER.info("[Tropeles] {} no puede el encargo de {} ({}): {}", villager.getName().getString(), name, tipo.palabra, no[0]);
            return "You have no way to do it right now: " + no[1] + " Say so in your own words, briefly.";
        }
        Modo base;
        UUID baseQuien;
        BlockPos baseEspera;
        if (w.modo == Modo.ENCARGO || w.modo == Modo.ALERTA && w.debajo == Modo.ENCARGO) {
            // a new errand instead of the one under way: that one is dropped, what it goes back to afterwards stays
            base = w.tras;
            baseQuien = w.trasQuien;
            baseEspera = w.trasEspera;
            if (w.encargo != null) {
                task.dejarEncargo(world, villager);
                w.encargo = null;
            }
        } else {
            base = w.modo == Modo.ALERTA ? w.debajo : w.modo;
            baseQuien = w.modo == Modo.ALERTA ? w.debajoQuien : w.quien;
            baseEspera = w.modo == Modo.ALERTA ? w.debajoEspera : w.espera;
        }
        if (base == Modo.CONOCIENDO || base == Modo.ENCARGO) {
            base = Modo.SIGUIENDO;
        }
        if (base == Modo.SIGUIENDO && baseQuien == null) {
            baseQuien = player.getUuid();
        }
        w.tras = base;
        w.trasQuien = baseQuien;
        w.trasEspera = baseEspera;
        w.entrega = null;
        w.oferta = null;
        w.encargo = e;
        w.change(villager, Modo.ENCARGO, player, null, name + " me pidió que " + e.pensando());
        task.empezarEncargo(world, villager, e);
        String[] line = e.empiezo();
        w.dicho(tipo.name(), line[0], line[1]);
        return "You set off at once to do it yourself, with your own hands, still connected: " + e.english() + ". When it is done you "
                + (e.traer ? "bring it to " + name + " and hand it over, then " : "tell " + name + " what you got and offer it, then ")
                + (base == Modo.SIGUIENDO ? "go back to their side." : base == Modo.ESPERANDO ? "go back to wait where you were."
                : base == Modo.OBRA ? "go back to building the house." : "go back to your own life.");
    }

    /**
     * "Deja eso", "deja de talar": the errand is dropped (what it got so far is offered); in its own life it stops what it
     * was doing and goes along with the player. Never the end of the connection: that is the menu's "stop". The account
     * for a language model; its own words in {@link #dicho}.
     */
    public static String parar(VillagerEntityMCA villager, PlayerEntity player, boolean spanish) {
        Voluntad w = of(villager);
        oyo(villager, player, spanish);
        String name = Companero.nombre(villager, player);
        if (w.encargo != null && villager.getWorld() instanceof ServerWorld world) {
            Encargo e = w.encargo;
            Map<Item, Integer> got = e.ganado(villager);
            ProspectingTask.of(villager).dejarEncargo(world, villager);
            w.encargo = null;
            w.volverTras(world, villager, name + " me dijo que lo deje");
            if (!got.isEmpty()) {
                w.oferta = new Entrega(e.para, got, true, "", "");
                w.ofertaHasta = world.getTime() + 2400;
            }
            w.dicho("PARAR", got.isEmpty() ? "Vale, lo dejo." : "Vale, lo dejo. Alcancé a conseguir " + Encargo.palabras(got, true) + ". ¿Te lo paso?",
                    got.isEmpty() ? "Okay, I'll leave it." : "Okay, I'll leave it. I got " + Encargo.palabras(got, false) + " so far. Want it?");
            return "You dropped the errand at once" + (got.isEmpty() ? "" : " (you offer them the " + Encargo.palabras(got, false) + " you got so far)")
                    + " and went back to " + (w.modo == Modo.SIGUIENDO ? "their side" : w.modo == Modo.ESPERANDO ? "wait where you were" : "your own life")
                    + ". You are still connected.";
        }
        if (w.modo == Modo.OBRA || w.modo == Modo.ALERTA && w.debajo == Modo.OBRA) {
            w.change(villager, Modo.SIGUIENDO, player, null, name + " me dijo que deje la casa por ahora");
            w.dicho("PARAR", "Vale, dejo la casa por ahora y voy contigo. Cuando quieras, dime que siga con la casa.",
                    "Okay, I'll leave the house for now and come with you. Whenever you want, tell me to go on with the house.");
            return "You left the house for now (it waits, paused, until they tell you to go on with it) and are at " + name
                    + "'s side, following them. You are still connected.";
        }
        if (w.modo == Modo.LIBRE) {
            w.change(villager, Modo.SIGUIENDO, player, null, name + " me dijo que deje lo que hacía");
            w.dicho("PARAR", "Vale, dejo lo que estaba haciendo. Voy contigo, " + name + ".", "Okay, I'll leave what I was doing. I'm coming with you, " + name + ".");
            return "You stopped what you were doing (your own plans and your mine wait for later) and are now at " + name
                    + "'s side, following them. You are still connected.";
        }
        boolean waiting = w.debajo() == Modo.ESPERANDO;
        w.dicho("PARAR", waiting ? "No estoy haciendo nada, " + name + ": te espero aquí." : "No estoy haciendo nada más, " + name + ": voy contigo.",
                waiting ? "I'm not doing anything, " + name + ": I'm waiting here for you." : "I'm not doing anything else, " + name + ": I'm with you.");
        return "You were not working on anything: you keep " + (waiting ? "waiting for " : "at the side of ") + name + ".";
    }

    /** Its own words for the order about {@code clave} it took a moment ago ({@link Encargo.Tipo} name, or "PARAR"), or null. */
    @Nullable
    public static String dicho(VillagerEntityMCA villager, String clave, boolean spanish) {
        Voluntad w = ALL.get(villager);
        if (w == null || !clave.equals(w.dichoClave) || System.currentTimeMillis() - w.dichoMs > 30_000L) {
            return null;
        }
        return spanish ? w.dichoEs : w.dichoEn;
    }

    private void dicho(String clave, String es, String en) {
        dichoClave = clave;
        dichoEs = es;
        dichoEn = en;
        dichoMs = System.currentTimeMillis();
    }

    /** What it is doing for someone right now, in its words ("Talo árboles para ti: 4 de 10 troncos."), or null with no errand. */
    @Nullable
    public static String haciendo(VillagerEntityMCA villager, boolean spanish) {
        Voluntad w = ALL.get(villager);
        Encargo e = w == null ? null : w.encargo;
        if (e == null) {
            return null;
        }
        return spanish ? "Estoy en lo que me pediste: " + e.pensando() + "." : "I'm on what you asked: " + e.english() + ".";
    }

    /**
     * "Sí", "dale", "pásamelos" (or "no, quédatelo") to what it just offered: it hands it over (brings it when too far
     * to throw), or keeps it. Its answer, or null when the message is no answer to an offer of its.
     */
    @Nullable
    public static String respuestaOferta(VillagerEntityMCA villager, ServerPlayerEntity player, String low, boolean spanish) {
        Voluntad w = ALL.get(villager);
        if (w == null || w.oferta == null || !(villager.getWorld() instanceof ServerWorld world) || !player.getUuid().equals(w.oferta.para())) {
            return null;
        }
        if (world.getTime() > w.ofertaHasta) {
            w.oferta = null;
            return null;
        }
        String t = low.trim();
        if (t.split("\\s+").length > 6) {
            return null;
        }
        String n = Companero.nombre(villager, player);
        if (SI.matcher(t).matches()) {
            Entrega en = w.oferta;
            w.oferta = null;
            if (villager.squaredDistanceTo(player) <= Acciones.HAND_OVER * Acciones.HAND_OVER && Retina.canSee(world, villager, player)) {
                Map<Item, Integer> gave = entregar(world, villager, player, en.cosas());
                return gave.isEmpty() ? (spanish ? "Uy, ya no lo tengo." : "Oops, I don't have it anymore.")
                        : (spanish ? "Toma, " + n + ": " : "Here you go, " + n + ": ") + Encargo.palabras(gave, spanish) + ".";
            }
            // too far to throw it: it brings it, and goes back to what it was doing after
            w.entrega = new Entrega(en.para(), en.cosas(), true, "", "");
            if (w.modo != Modo.SIGUIENDO || !player.getUuid().equals(w.quien)) {
                if (w.encargo == null) {
                    w.tras = w.debajo();
                    w.trasQuien = w.modo == Modo.ALERTA ? w.debajoQuien : w.quien;
                    w.trasEspera = w.modo == Modo.ALERTA ? w.debajoEspera : w.espera;
                }
                w.change(villager, Modo.SIGUIENDO, player, null, "le llevo a " + n + " lo que le ofrecí");
            }
            return spanish ? "¡Voy! Te lo llevo." : "Coming! I'll bring it over.";
        }
        if (NO_GRACIAS.matcher(t).matches()) {
            w.oferta = null;
            return spanish ? "Vale, me lo quedo entonces." : "Okay, I'll keep it then.";
        }
        return null;
    }

    /**
     * A move button of the villager's menu, for a connected villager: following, waiting and its own life are its
     * will's, with a word to the one who pressed it (MCA's FOLLOW would end the connection, its STAY would leave the
     * villager a brain without its own life).
     */
    public static void menu(VillagerEntityMCA villager, PlayerEntity player, net.mca.entity.ai.MoveState state) {
        Voluntad w = of(villager);
        boolean es = w.spanish();
        String n = Companero.nombre(villager, player);
        switch (state) {
            case FOLLOW -> {
                seguir(villager, player, es);
                w.alMenu(villager, player, "¡Vamos, " + n + "! Voy contigo.", "Let's go, " + n + "! I'm with you.");
            }
            case STAY -> {
                esperar(villager, player, es);
                w.alMenu(villager, player, "Vale, aquí te espero.", "Okay, I'll wait right here for you.");
            }
            default -> {
                libre(villager, player, es);
                w.alMenu(villager, player, "Bueno, sigo con lo mío.", "Alright, back to my own thing.");
            }
        }
    }

    /** A chore button of the menu, for a connected villager: the errand ({@link #encargar}), never MCA's chore; escorting is following. */
    public static void menu(VillagerEntityMCA villager, PlayerEntity player, Chore chore) {
        Voluntad w = of(villager);
        boolean es = w.spanish();
        Encargo.Tipo tipo = Encargo.Tipo.of(chore);
        if (tipo == null) {
            menu(villager, player, net.mca.entity.ai.MoveState.FOLLOW);
            return;
        }
        encargar(villager, player, tipo, "", es);
        w.alMenu(villager, player, w.dichoEs, w.dichoEn);
    }

    private void alMenu(VillagerEntityMCA villager, PlayerEntity player, String es, String en) {
        if (villager.getWorld() instanceof ServerWorld world) {
            hablar(world, villager, player, es, en);
        }
    }

    /** One tick of the errand: a fight first if one comes, its eyes always on, its hands on what it was asked. */
    private void encargoTick(ServerWorld world, VillagerEntityMCA villager, @Nullable ProspectingTask task) {
        Encargo e = encargo;
        if (e == null || task == null) {
            encargo = null;
            volverTras(world, villager, "no tenía encargo");
            return;
        }
        PlayerEntity player = player(world);
        if (defend(world, villager, player, DEFEND_WAIT)) {
            return;
        }
        if ((villager.age & 1) == 0) {
            Retina.look(world, villager);
        }
        Encargo.Fin fin = task.food() <= 4 && ProspectingTask.comidaEnMochila(villager) == 0 ? Encargo.Fin.HAMBRE
                : world.getTime() - desde > e.tipo.limite ? Encargo.Fin.TIEMPO : task.encargo(world, villager, e);
        if (fin != Encargo.Fin.SIGUE) {
            terminarEncargo(world, villager, task, e, fin);
        }
    }

    /**
     * One tick of the house: a fight first if one comes, its eyes always on, then its hands on the building
     * ({@link Constructor}). Starving with nothing to eat, or night with a bed to go to, it leaves the house for a
     * while by itself (down from any scaffold first) and comes back to it on its own ({@link #volverAObra}).
     */
    private void obraTick(ServerWorld world, VillagerEntityMCA villager, @Nullable ProspectingTask task) {
        Obra o = ObrasMundo.get(world).de(villager.getUuid());
        if (o == null || task == null) {
            volverTras(world, villager, "ya no tengo ninguna casa que hacer");
            return;
        }
        PlayerEntity player = player(world);
        if (defend(world, villager, player, DEFEND_WAIT)) {
            return;
        }
        Retina.look(world, villager);
        Constructor c = Constructor.de(villager);
        boolean hambre = task.food() <= 4 && ProspectingTask.comidaEnMochila(villager) == 0;
        boolean sueno = !hambre && (villager.age % 100) == 0 && task.quiereDormir(world, villager);
        if (hambre || sueno) {
            if (c.enAndamio(world, villager)) {
                c.bajarYa(); // off the scaffold first, then the meal or the bed
            } else {
                pausaInterna = true;
                try {
                    change(villager, Modo.LIBRE, null, null, hambre ? "tengo hambre: dejo la casa un rato para comer" : "se hizo de noche: dejo la casa hasta mañana");
                } finally {
                    pausaInterna = false;
                }
                contar(villager, hambre ? "Tengo hambre: dejo la casa un rato para buscar comida y vuelvo." : "Se hizo de noche: me voy a dormir y mañana sigo con la casa.",
                        hambre ? "I'm hungry: I'm leaving the house for a bit to find food, then I'll be back." : "It's night: I'm off to bed, I'll go on with the house tomorrow.");
                return;
            }
        }
        if (c.tick(world, villager, task, o) == Constructor.Fin.LISTA) {
            terminarObra(world, villager, o);
        }
    }

    /** The last block is set and its things are put away: the house is theirs. */
    private void terminarObra(ServerWorld world, VillagerEntityMCA villager, Obra o) {
        ObrasMundo.get(world).quitar(o);
        Proyeccion.borrar(world, o);
        Constructor.olvidar(villager.getUuid());
        String n = o.paraNombre.isEmpty() ? "alguien" : o.paraNombre;
        MCA.LOGGER.info("[Tropeles] {} termina la casa de {} en {}", villager.getName().getString(), n, o.donde());
        Recuerdos.anotar(villager, "obra", "terminé la casa de " + n + " en " + o.donde() + ": dos pisos, de " + o.madera.es + ", hecha con mis manos",
                o.centro());
        ServerPlayerEntity para = o.para == null ? null : jugador(world, o.para);
        tras = obraTras;
        trasQuien = obraTrasQuien;
        trasEspera = obraTrasEspera;
        obraTras = Modo.LIBRE;
        obraTrasQuien = null;
        obraTrasEspera = null;
        volverTras(world, villager, "terminé la casa de " + n);
        hablar(world, villager, para, "¡Lista la casa" + (o.paraNombre.isEmpty() ? "" : ", " + o.paraNombre) + "! Está en " + o.donde() + ". Pasa, es tuya.",
                "The house is done" + (o.paraNombre.isEmpty() ? "" : ", " + o.paraNombre) + "! It's at " + o.donde() + ". Come in, it's yours.");
    }

    /**
     * "Constrúyeme una casa", "hazme una casa aquí", "una casa de abeto", "sigue con la casa": a two-floor house for
     * {@code player}, all of it by its own hands. A house under way is taken up again (moved to where they look while
     * almost nothing of it stands); otherwise it picks the spot ("aquí": where they look; else the best ground near
     * them, the door their way), shows the whole plan as a see-through ghost and sets off for the materials. The
     * account for a language model; its own words in {@link #dicho}.
     */
    public static String construir(VillagerEntityMCA villager, PlayerEntity player, String words, boolean spanish) {
        Voluntad w = of(villager);
        oyo(villager, player, spanish);
        String name = Companero.nombre(villager, player);
        if (!(villager.getWorld() instanceof ServerWorld world)) {
            return "You have no way to do that right now.";
        }
        ProspectingTask task = ProspectingTask.of(villager);
        ObrasMundo obras = ObrasMundo.get(world);
        String low = words == null ? "" : words.toLowerCase(java.util.Locale.ROOT);
        boolean aqui = AQUI.matcher(low).find();
        Obra o = obras.de(villager.getUuid());
        boolean nueva = o == null;
        String[] why = {"", ""};
        if (o != null) {
            if (aqui && o.hechas(world) <= 3) {
                Sitio.Elegido s = Sitio.buscar(world, villager, o.plano(), Sitio.mirada(player), player.getPos(), 0, 8, o.id, why);
                if (s != null) {
                    o.mover(s.origen(), s.frente());
                }
            }
            o.pausaJugador = false;
        } else {
            Plano.Madera madera = Plano.Madera.palabra(low);
            if (madera == null) {
                madera = maderaDe(villager);
            }
            Plano plano = Plano.casa(madera);
            Sitio.Elegido s = aqui ? Sitio.buscar(world, villager, plano, Sitio.mirada(player), player.getPos(), 0, 8, null, why)
                    : Sitio.buscar(world, villager, plano, player.getBlockPos(), player.getPos(), 7, 28, null, why);
            if (s == null) {
                w.dicho("OBRA", "No encuentro dónde hacer una casa " + (aqui ? "ahí" : "por aquí") + ", " + name + ": " + why[0] + ". ¿Me muestras otro sitio?",
                        "I can't find anywhere to put a house " + (aqui ? "there" : "around here") + ", " + name + ": " + why[1] + ". Can you show me another spot?");
                return "You can't find anywhere to build a house " + (aqui ? "where they are looking" : "around here") + ": " + why[1]
                        + ". Say so, and ask them to look at (or stand on) a flatter, dry, free spot and ask again.";
            }
            o = new Obra(UUID.randomUUID().toString().substring(0, 8), villager.getUuid(), villager.getName().getString(), s.origen(), s.frente(), madera);
            o.creada = world.getTime();
            obras.poner(o);
        }
        o.para = player.getUuid();
        o.paraNombre = name;
        obras.cambio();
        Proyeccion.enviar(world, o);
        if (w.encargo != null) {
            task.dejarEncargo(world, villager);
            w.encargo = null;
        }
        if (w.modo != Modo.OBRA && !(w.modo == Modo.ALERTA && w.debajo == Modo.OBRA)) {
            Modo base = w.modo == Modo.ALERTA ? w.debajo : w.modo;
            UUID baseQuien = w.modo == Modo.ALERTA ? w.debajoQuien : w.quien;
            BlockPos baseEspera = w.modo == Modo.ALERTA ? w.debajoEspera : w.espera;
            if (base == Modo.CONOCIENDO || base == Modo.ENCARGO || base == Modo.OBRA) {
                base = Modo.SIGUIENDO;
            }
            if (base == Modo.SIGUIENDO && baseQuien == null) {
                baseQuien = player.getUuid();
            }
            w.obraTras = base;
            w.obraTrasQuien = baseQuien;
            w.obraTrasEspera = baseEspera;
            w.change(villager, Modo.OBRA, player, null, name + (nueva ? " me pidió una casa" : " me dijo que siga con su casa"));
        }
        Plano plano = o.plano();
        if (nueva) {
            w.dicho("OBRA", "¡Va, " + name + "! Te hago una casa de dos pisos de " + o.madera.es + " en " + o.donde() + ": ahí ves el plano. La hago toda yo;"
                            + " necesito " + Constructor.materiales(plano, true) + ", así que primero voy por material.",
                    "You got it, " + name + "! I'll build you a two-floor " + o.madera.en + " house at " + o.donde() + ": you can see the plan there. I'll do all"
                            + " of it myself; I need " + Constructor.materiales(plano, false) + ", so I'm off for materials first.");
            MCA.LOGGER.info("[Tropeles] {} empieza una casa de {} para {} en {} (frente {})", villager.getName().getString(), o.madera.es, name, o.donde(), o.frente);
            return "You are now building a two-floor " + o.madera.en + " house for " + name + " at " + o.donde() + ", all by your own hands (chopping,"
                    + " digging stone, finding sand, crafting, smelting): they can see the whole plan as a see-through ghost. You need "
                    + Constructor.materiales(plano, false) + ". Tell them so, briefly and gladly.";
        }
        w.dicho("OBRA", "¡Sigo con tu casa, " + name + "! " + Constructor.faltas(world, villager, o, true),
                "Back on your house, " + name + "! " + Constructor.faltas(world, villager, o, false));
        return "You go back to building the house for " + name + ": " + Constructor.estado(world, villager, o, false) + " Say so briefly.";
    }

    /** "Ya no hagas la casa", "cancela la casa": the house is given up for good; what stands of it stays. */
    public static String cancelarObra(VillagerEntityMCA villager, PlayerEntity player, boolean spanish) {
        Voluntad w = of(villager);
        oyo(villager, player, spanish);
        String name = Companero.nombre(villager, player);
        if (!(villager.getWorld() instanceof ServerWorld world)) {
            return "You have no way to do that right now.";
        }
        ObrasMundo obras = ObrasMundo.get(world);
        Obra o = obras.de(villager.getUuid());
        if (o == null) {
            w.dicho("OBRA", "No estoy haciendo ninguna casa, " + name + ".", "I'm not building any house, " + name + ".");
            return "You are not building any house. Say so.";
        }
        int done = o.hechas(world);
        Constructor c = Constructor.si(villager);
        if (c != null) {
            c.soltar(world, villager, ProspectingTask.of(villager));
        }
        obras.quitar(o);
        Proyeccion.borrar(world, o);
        Constructor.olvidar(villager.getUuid());
        MCA.LOGGER.info("[Tropeles] {} deja la casa de {} para siempre ({} bloques puestos)", villager.getName().getString(), name, done);
        if (w.modo == Modo.OBRA || w.modo == Modo.ALERTA && w.debajo == Modo.OBRA) {
            w.tras = w.obraTras;
            w.trasQuien = w.obraTrasQuien;
            w.trasEspera = w.obraTrasEspera;
            w.volverTras(world, villager, name + " me dijo que ya no haga la casa");
        }
        w.dicho("OBRA", done > 0 ? "Vale, ya no hago la casa. Lo que alcancé a poner queda ahí." : "Vale, no hago la casa.",
                done > 0 ? "Okay, no more house. What I already set stays there." : "Okay, I won't build the house.");
        return "You gave up the house for good (the " + done + " blocks already set stay where they are) and went back to "
                + (w.modo == Modo.SIGUIENDO ? "their side" : w.modo == Modo.ESPERANDO ? "wait where you were" : "your own life") + ". You are still connected.";
    }

    /** It has a house under way (paused or not). */
    public static boolean tieneObra(VillagerEntityMCA villager) {
        return villager.getWorld() instanceof ServerWorld world && ObrasMundo.get(world).de(villager.getUuid()) != null;
    }

    /** How its house is going, in its words, or null when it builds none. */
    @Nullable
    public static String obraEstado(VillagerEntityMCA villager, boolean spanish) {
        if (!(villager.getWorld() instanceof ServerWorld world)) {
            return null;
        }
        Obra o = ObrasMundo.get(world).de(villager.getUuid());
        return o == null ? null : Constructor.estado(world, villager, o, spanish);
    }

    /**
     * From its own life back to the house it was building and not told to leave, once it is fed, rested and up on the
     * land (from under ground it first goes up and home). True when it took it up again.
     */
    public static boolean volverAObra(ServerWorld world, VillagerEntityMCA villager, ProspectingTask task, boolean bajoTierra) {
        if (!conectado(villager)) {
            return false;
        }
        Voluntad w = of(villager);
        if (w.modo != Modo.LIBRE) {
            return false;
        }
        Obra o = ObrasMundo.get(world).de(villager.getUuid());
        if (o == null || o.pausaJugador) {
            return false;
        }
        if (task.food() <= 6 && ProspectingTask.comidaEnMochila(villager) == 0 || task.quiereDormir(world, villager)) {
            return false;
        }
        long now = world.getTime();
        if (bajoTierra) {
            if (now - w.llamadoObra > 2400) {
                w.llamadoObra = now;
                ProspectingTask.callHome(villager, false);
                MCA.LOGGER.info("[Tropeles] {} sube para volver a su casa en obra", villager.getName().getString());
            }
            return false;
        }
        ServerPlayerEntity p = o.para == null ? null : jugador(world, o.para);
        w.change(villager, Modo.OBRA, p, null, "vuelvo a la casa de " + (o.paraNombre.isEmpty() ? "alguien" : o.paraNombre));
        if (p == null && o.para != null) {
            w.quien = o.para;
            w.quienNombre = o.paraNombre;
        }
        contar(villager, "Vuelvo a la casa.", "Back to the house.");
        return true;
    }

    /** The wood its house would be of: what it carries most of, else the trees it has seen around, else oak. */
    private static Plano.Madera maderaDe(VillagerEntityMCA villager) {
        Map<Plano.Madera, Integer> votes = new java.util.EnumMap<>(Plano.Madera.class);
        SimpleInventory inv = villager.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && s.getItem() instanceof net.minecraft.item.BlockItem b) {
                Plano.Madera m = Plano.Madera.de(b.getBlock());
                if (m != null) {
                    votes.merge(m, s.getCount(), Integer::sum);
                }
            }
        }
        if (votes.isEmpty()) {
            BlockPos feet = Mining.pies(villager);
            for (VisualMemory.SeenBlock seen : Retina.memory(villager).blocks(b -> b.state != null && b.state.isIn(net.minecraft.registry.tag.BlockTags.LOGS)
                    && b.pos.isWithinDistance(feet, 48))) {
                Plano.Madera m = Plano.Madera.de(seen.state.getBlock());
                if (m != null) {
                    votes.merge(m, 1, Integer::sum);
                }
            }
        }
        Plano.Madera best = Plano.Madera.ROBLE;
        int most = 0;
        for (Map.Entry<Plano.Madera, Integer> e : votes.entrySet()) {
            if (e.getValue() > most) {
                most = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    /** The errand is over: back to what it was doing for them, with what it got handed over, offered, or explained. */
    private void terminarEncargo(ServerWorld world, VillagerEntityMCA villager, ProspectingTask task, Encargo e, Encargo.Fin fin) {
        Map<Item, Integer> got = e.ganado(villager);
        String[] falta = fin == Encargo.Fin.FALTA ? task.noPuede(world, villager, e) : null;
        task.dejarEncargo(world, villager);
        encargo = null;
        String n = e.paraNombre;
        boolean hay = !got.isEmpty();
        String whatEs = Encargo.palabras(got, true);
        String whatEn = Encargo.palabras(got, false);
        String[] why = porQue(e, fin, falta);
        MCA.LOGGER.info("[Tropeles] {} termina el encargo de {} ({}, {}): {}", villager.getName().getString(), n, e.tipo.palabra, fin, hay ? whatEs : "nada");
        Recuerdos.anotar(villager, "encargo", "terminé lo que me pidió " + n + " (" + e.tipo.palabra + "): " + why[0]
                + (hay ? " Conseguí " + whatEs + "." : " No conseguí nada."), Mining.pies(villager));
        ServerPlayerEntity para = jugador(world, e.para);
        if (hay && e.traer && para != null) {
            // to their side with it, handed over there; then back to what it was doing
            change(villager, Modo.SIGUIENDO, para, null, "le llevo a " + n + " " + whatEs);
            entrega = new Entrega(e.para, got, true, "", "");
            if (fin != Encargo.Fin.LISTO) {
                hablar(world, villager, para, why[0] + " Te llevo " + whatEs + ".", why[1] + " I'm bringing you " + whatEn + ".");
            }
            return;
        }
        volverTras(world, villager, "terminé lo que me pidió " + n);
        if (para == null) {
            return; // they are gone: it remembers, and says nothing to nobody
        }
        if (!hay) {
            hablar(world, villager, para, why[0], why[1]);
            return;
        }
        String es = (fin == Encargo.Fin.LISTO ? "¡Listo, " + n + "! Conseguí " : why[0] + " Conseguí ") + whatEs + ". ¿Te lo paso?";
        String en = (fin == Encargo.Fin.LISTO ? "Done, " + n + "! I got " : why[1] + " I got ") + whatEn + ". Want it?";
        if (modo == Modo.SIGUIENDO && para.getUuid().equals(quien)) {
            entrega = new Entrega(e.para, got, false, es, en); // said at their side
        } else {
            hablar(world, villager, para, es, en);
            oferta = new Entrega(e.para, got, true, es, en);
            ofertaHasta = world.getTime() + 2400;
        }
    }

    /** Why it stopped, in its words (Spanish, English): done, nothing more there, a full bag, the night, what it lacks. */
    private static String[] porQue(Encargo e, Encargo.Fin fin, @Nullable String[] falta) {
        String n = e.paraNombre;
        return switch (fin) {
            case LISTO, SIGUE -> new String[]{"¡Listo, " + n + "!", "Done, " + n + "!"};
            case NADA -> switch (e.tipo) {
                case TALAR, HACER -> new String[]{"No encontré más árboles por aquí, " + n + ".", "I couldn't find any more trees around here, " + n + "."};
                case COSECHAR -> new String[]{"No hay nada más maduro por aquí, " + n + ".", "There's nothing else ripe around here, " + n + "."};
                case CAZAR -> new String[]{"No encontré nada que cazar por aquí, " + n + ".", "I couldn't find anything to hunt around here, " + n + "."};
                case PESCAR -> new String[]{"No encontré agua buena para pescar por aquí, " + n + ".", "I couldn't find good water to fish in around here, " + n + "."};
            };
            case LLENO -> new String[]{"Se me llenó la mochila, " + n + ".", "My bag is full, " + n + "."};
            case NOCHE -> new String[]{"Se hizo de noche, mejor dejo la caza.", "It got dark, I'd better stop hunting."};
            case FALTA -> falta != null ? falta : e.tipo == Encargo.Tipo.PESCAR
                    ? new String[]{"Se me rompió la caña y no tengo hilo para otra.", "My rod broke and I have no string for another."}
                    : new String[]{"Me quedé sin material para eso.", "I ran out of material for that."};
            case HAMBRE -> new String[]{"Tengo mucha hambre, " + n + ", tengo que comer algo primero.", "I'm starving, " + n + ", I need to eat something first."};
            case TIEMPO -> new String[]{"Esto me está tomando demasiado, " + n + ".", "This is taking me too long, " + n + "."};
        };
    }

    /** Back to what it was doing for them before the errand: at their side, waiting on its spot, or its own life. */
    private void volverTras(ServerWorld world, VillagerEntityMCA villager, String why) {
        Modo back = tras;
        UUID who = trasQuien;
        BlockPos spot = trasEspera;
        tras = Modo.LIBRE;
        trasQuien = null;
        trasEspera = null;
        PlayerEntity p = who == null ? null : jugador(world, who);
        Obra obra = back == Modo.OBRA ? ObrasMundo.get(world).de(villager.getUuid()) : null;
        if (obra != null) {
            // an errand in the middle of the house: back to building it
            obra.pausaJugador = false;
            ObrasMundo.get(world).cambio();
            tras = obraTras;
            trasQuien = obraTrasQuien;
            trasEspera = obraTrasEspera;
            change(villager, Modo.OBRA, p, null, why);
            if (p == null && who != null) {
                quien = who;
            }
            return;
        }
        if (back == Modo.SIGUIENDO && who != null) {
            change(villager, Modo.SIGUIENDO, p, null, why);
        } else if (back == Modo.ESPERANDO && spot != null) {
            change(villager, Modo.ESPERANDO, p, spot, why);
        } else {
            change(villager, Modo.LIBRE, null, null, why);
            return;
        }
        if (p == null) {
            // the one it was with is not here right now: it keeps to them, and waits for them to show up
            quien = who;
            quienRef = new WeakReference<>(null);
            save(villager);
        }
    }

    /** That player, when logged in, in this world and playing. */
    @Nullable
    private static ServerPlayerEntity jugador(ServerWorld world, UUID id) {
        ServerPlayerEntity p = world.getServer() == null ? null : world.getServer().getPlayerManager().getPlayer(id);
        return p == null || p.getWorld() != world || !p.isAlive() || p.isSpectator() ? null : p;
    }

    /** Throws {@code player} what it got for them, from the bag first, then its hands and body: what it gave. */
    private static Map<Item, Integer> entregar(ServerWorld world, VillagerEntityMCA villager, ServerPlayerEntity player, Map<Item, Integer> cosas) {
        Map<Item, Integer> dado = new LinkedHashMap<>();
        SimpleInventory inv = villager.getInventory();
        for (Map.Entry<Item, Integer> it : cosas.entrySet()) {
            int left = it.getValue();
            for (int i = 0; i < inv.size() && left > 0; i++) {
                ItemStack s = inv.getStack(i);
                if (!s.isEmpty() && s.isOf(it.getKey())) {
                    int k = Math.min(left, s.getCount());
                    Acciones.throwTo(world, villager, player, s.split(k));
                    left -= k;
                    dado.merge(it.getKey(), k, Integer::sum);
                }
            }
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack s = villager.getEquippedStack(slot);
                if (left > 0 && !s.isEmpty() && s.isOf(it.getKey())) {
                    int k = Math.min(left, s.getCount());
                    Acciones.throwTo(world, villager, player, s.split(k));
                    left -= k;
                    dado.merge(it.getKey(), k, Integer::sum);
                }
            }
        }
        inv.markDirty();
        if (!dado.isEmpty()) {
            MCA.LOGGER.info("[Tropeles] {} le pasa a {}: {}", villager.getName().getString(), player.getName().getString(), Encargo.palabras(dado, true));
            Recuerdos.anotar(villager, "dar", "le di a " + player.getName().getString() + " " + Encargo.palabras(dado, true), Mining.pies(villager));
        }
        return dado;
    }

    /** Says a line to {@code player} in its language, as part of their conversation (the models remember it). */
    private void hablar(ServerWorld world, VillagerEntityMCA villager, @Nullable PlayerEntity player, String es, String en) {
        if (player == null) {
            contar(villager, es, en);
            return;
        }
        String line = spanish() ? es : en;
        iniciativa.decir(villager, player, line, world.getTime());
        if (player instanceof ServerPlayerEntity sp) {
            net.mca.entity.ai.chatAI.OpenAIChatAI.remember(villager.getUuid(), sp.getUuid(), "…", line);
        }
    }

    /** Meeting the one who connected it: a fight first if one comes, else the meeting; then it goes at their side. */
    private void meetTick(ServerWorld world, VillagerEntityMCA villager) {
        PlayerEntity player = player(world);
        if (player == null || villager.squaredDistanceTo(player) > 48.0 * 48.0) {
            // gone before they met: it gets on with its own life
            change(villager, Modo.LIBRE, null, null, "se fue antes de que nos conociéramos");
            return;
        }
        if (defend(world, villager, player, DEFEND_ALERT)) {
            return;
        }
        if (Companero.conocer(world, villager, player, world.getTime() - desde)) {
            change(villager, Modo.SIGUIENDO, player, null, "ya nos conocimos: voy con " + Companero.nombre(villager, player));
        }
    }

    /** Hunger at the player's side ({@link Companero#hambre}). */
    private Companero.Pide hunger(ServerWorld world, VillagerEntityMCA villager, ProspectingTask task) {
        PlayerEntity player = player(world);
        return player == null ? Companero.Pide.NADA : Companero.hambre(world, villager, player, task);
    }

    /** Nobody had food for it: off to find some itself, in its own life, to come back to the player after. */
    private void findFood(ServerWorld world, VillagerEntityMCA villager, ProspectingTask task) {
        UUID back = quien;
        task.buscarComida();
        change(villager, Modo.LIBRE, null, null, "voy a buscar comida y vuelvo con " + (quienNombre.isEmpty() ? "quien estaba" : quienNombre));
        volver = back;
        volverDesde = world.getTime();
    }

    /** In its own life after going for food: once it has eaten, back to the player it left. */
    private void backTick(ServerWorld world, VillagerEntityMCA villager, @Nullable ProspectingTask task) {
        if (volver == null || task == null || (villager.age % 20) != 0) {
            return;
        }
        if (world.getTime() - volverDesde > 12000) {
            volver = null; // ten minutes: it stays in its own life
            return;
        }
        if (task.food() < 16 && ProspectingTask.comidaEnMochila(villager) < 6) {
            return;
        }
        PlayerEntity player = world.getServer() == null ? null : world.getServer().getPlayerManager().getPlayer(volver);
        if (player == null || player.getWorld() != world || !player.isAlive() || player.isSpectator()) {
            return;
        }
        volver = null;
        change(villager, Modo.SIGUIENDO, player, null, "ya comí: vuelvo con " + player.getName().getString());
        contar(villager, "¡Listo, ya comí! Vuelvo contigo, " + Companero.nombre(villager, player) + ".",
                "Done, I've eaten! Coming back to you, " + Companero.nombre(villager, player) + ".");
    }

    /**
     * Following and getting nowhere ({@link Atasco}): beside the player, or with the player gone, it is where it should
     * be; otherwise the companion's steps start over, and in the end it tells the player where it is stuck.
     */
    private void followStuck(ServerWorld world, VillagerEntityMCA villager) {
        PlayerEntity player = player(world);
        boolean near = player == null || villager.squaredDistanceTo(player) < 5.0 * 5.0 || villager.isSleeping() || CombatMind.busy(villager);
        Atasco.Remedio r = atasco.tick(world, villager, near, pensando);
        switch (r) {
            case NINGUNO -> {
            }
            case AYUDA -> {
                BlockPos at = Mining.pies(villager);
                contar(villager, "No puedo llegar hasta ti, estoy atascado en " + at.getX() + " " + at.getY() + " " + at.getZ() + ". ¿Me ayudas?",
                        "I can't get to you, I'm stuck at " + at.getX() + " " + at.getY() + " " + at.getZ() + ". Can you help me?");
            }
            default -> {
                // the companion's half-made step and its idea of where the player stood go: it plans its way to them again
                follow.release(world, villager);
                villager.getNavigation().stop();
            }
        }
    }

    private void alertTick(ServerWorld world, VillagerEntityMCA villager) {
        long now = world.getTime();
        PlayerEntity player = player(world);
        villager.getBrain().forget(MemoryModuleType.WALK_TARGET);
        if (defend(world, villager, player, DEFEND_ALERT)) {
            return;
        }
        Motor.standStill(villager);
        if (player != null) {
            sinJugador = -1;
            face(villager, player);
        } else if (sinJugador < 0) {
            sinJugador = now;
        }
        if ((villager.age & 1) == 0) {
            Retina.look(world, villager);
        }
        if (agachado) {
            // a crouch reason lasts a few seconds unless asked for again: asked for every tick it still holds
            crouch(villager, true);
        } else if ((villager.age % 20) == 0) {
            judgeCaution(villager);
        }
        if (sinJugador >= 0 && now - sinJugador > 200) {
            resume(villager, "se fue quien me avisó");
            return;
        }
        if (now - oido > ALERT_SILENCE) {
            resume(villager, "nadie me dijo nada en un rato");
            if (player != null) {
                iniciativa.decir(villager, player, sigoLinea(villager, spanish()), now);
            }
        }
    }

    private void waitTick(ServerWorld world, VillagerEntityMCA villager) {
        long now = world.getTime();
        PlayerEntity player = player(world);
        if (defend(world, villager, player, DEFEND_WAIT)) {
            return;
        }
        if (espera != null && Mining.pies(villager).getSquaredDistance(espera) > 2.5 * 2.5) {
            // pushed off its spot, or it stepped off it in a fight: back to where it was asked to wait
            if ((villager.age % 20) == 0) {
                villager.getNavigation().startMovingTo(espera.getX() + 0.5, espera.getY(), espera.getZ() + 0.5, 0.8);
            }
            return;
        }
        Motor.standStill(villager);
        villager.getBrain().forget(MemoryModuleType.WALK_TARGET);
        if (mirada.sweeping()) {
            mirada.tick(world, villager);
            return;
        }
        if (now >= nextSweep) {
            // a look all around now and then, the way someone waiting keeps an eye out
            nextSweep = now + 300 + villager.getRandom().nextInt(200);
            mirada.start(villager);
            return;
        }
        if (player != null && villager.squaredDistanceTo(player) < 24 * 24 && (now < lookUntil || villager.getRandom().nextInt(80) == 0)) {
            if (now >= lookUntil) {
                lookUntil = now + 40;
            }
            face(villager, player);
        }
        if ((villager.age & 1) == 0) {
            Retina.look(world, villager);
        }
    }

    /** The player it was with is gone (another world, offline): it stands where it is, and after a long while goes back to its life. */
    private void missing(ServerWorld world, VillagerEntityMCA villager) {
        long now = world.getTime();
        if (sinJugador < 0) {
            sinJugador = now;
        }
        Motor.standStill(villager);
        if ((villager.age & 1) == 0) {
            Retina.look(world, villager);
        }
        if (now - sinJugador > LOST_PLAYER) {
            change(villager, Modo.LIBRE, null, null, quienNombre + " se fue hace rato");
        }
    }

    /** A monster within {@code radius} (or whatever hurts it) is fought. True while fighting. */
    private boolean defend(ServerWorld world, VillagerEntityMCA villager, @Nullable PlayerEntity player, double radius) {
        if (enemigo != null && (!enemigo.isAlive() || enemigo.isRemoved() || enemigo.getWorld() != world
                || enemigo.squaredDistanceTo(villager) > (radius + 4) * (radius + 4))) {
            enemigo = null;
            CombatMind.disengage(villager);
        }
        if (enemigo == null && (villager.age & 3) == 0) {
            LivingEntity threat = CombatMind.pickTarget(world, villager, null, radius);
            if (threat != null && threat.isAlive()) {
                enemigo = threat;
                MCA.LOGGER.info("[Tropeles] {} se defiende de {} ({})", villager.getName().getString(), threat.getName().getString(), modo.palabra());
            }
        }
        if (enemigo == null) {
            return false;
        }
        CombatMind.fight(world, villager, player, enemigo);
        return true;
    }

    /** Head and body toward the player's eyes, like turning round to someone who spoke. */
    private void face(VillagerEntityMCA villager, PlayerEntity player) {
        Vec3d eye = player.getEyePos();
        Hands.aim(villager, eye);
        double dx = eye.x - villager.getX();
        double dz = eye.z - villager.getZ();
        if (dx * dx + dz * dz > 0.09) {
            float yaw = (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
            villager.setHeadYaw(yaw);
            villager.setBodyYaw(yaw);
            villager.setYaw(yaw);
        }
    }

    /** In the rock, or a monster seen close by lately: it crouches. */
    private void judgeCaution(VillagerEntityMCA villager) {
        if (!(villager.getWorld() instanceof ServerWorld world)) {
            return;
        }
        String why = null;
        if (Entorno.bajoTierra(world, villager)) {
            why = "bajo tierra";
        } else {
            String monster = monsterSeen(world, villager);
            if (monster != null) {
                why = "vi " + monster + " cerca";
            }
        }
        if (why != null && !agachado) {
            porQue = why;
            crouch(villager, true);
            agachado = true;
            MCA.LOGGER.info("[Tropeles] {} se agacha con cuidado ({})", villager.getName().getString(), why);
        }
    }

    /** The name of a monster its eyes saw within {@value #MONSTER_NEAR} blocks lately, or null. */
    @Nullable
    static String monsterSeen(ServerWorld world, VillagerEntityMCA villager) {
        long now = world.getTime();
        for (VisualMemory.SeenEntity seen : Retina.memory(villager).entities(now, MONSTER_RECENT)) {
            if (seen.type.getSpawnGroup() == SpawnGroup.MONSTER && seen.pos != null
                    && seen.pos.squaredDistanceTo(villager.getPos()) < MONSTER_NEAR * MONSTER_NEAR) {
                return seen.type.getName().getString();
            }
        }
        return null;
    }

    /** {@link Postura}, never letting a body that cannot crouch yet stop the will. */
    private void crouch(VillagerEntityMCA villager, boolean on) {
        try {
            Postura.agachar(villager, CROUCH, on);
        } catch (RuntimeException e) {
            if (!loggedError) {
                loggedError = true;
                MCA.LOGGER.warn("[Tropeles] {} no pudo {}: {}", villager.getName().getString(), on ? "agacharse" : "pararse", e.toString());
            }
        }
    }

    /** The player of this mode, when it is in this world, alive and playing. */
    @Nullable
    PlayerEntity player(ServerWorld world) {
        if (quien == null) {
            return null;
        }
        PlayerEntity player = world.getServer() == null ? null : world.getServer().getPlayerManager().getPlayer(quien);
        if (player == null) {
            // not a logged-in player (the checks' stand-in): the one it was told by, while it exists
            player = quienRef.get();
            if (player != null && player.isRemoved()) {
                player = null;
            }
        }
        if (player == null || player.getWorld() != world || !player.isAlive() || player.isSpectator()) {
            return null;
        }
        quienRef = new WeakReference<>(player);
        quienNombre = player.getName().getString();
        return player;
    }

    // ------------------------------------------------------------------ changing its mind

    private void change(VillagerEntityMCA villager, Modo to, @Nullable PlayerEntity player, @Nullable BlockPos spot, String why) {
        Modo from = modo;
        if (to == Modo.ALERTA && from != Modo.ALERTA) {
            // a meeting cut short goes on at their side; an errand goes on after the alert
            debajo = from == Modo.CONOCIENDO ? Modo.SIGUIENDO : from;
            debajoQuien = quien;
            debajoEspera = espera;
        }
        leave(villager, from, to);
        modo = to;
        if (player != null) {
            quien = player.getUuid();
            quienRef = new WeakReference<>(player);
            quienNombre = player.getName().getString();
        } else if (to == Modo.LIBRE) {
            quien = null;
            quienRef = new WeakReference<>(null);
            quienNombre = "";
        }
        espera = to == Modo.ESPERANDO ? spot : to == Modo.ALERTA ? espera : null;
        desde = villager.getWorld().getTime();
        sinJugador = -1;
        enemigo = null;
        if (to != Modo.ALERTA && from == Modo.ALERTA) {
            debajoQuien = null;
            debajoEspera = null;
        }
        save(villager);
        think(villager);
        MCA.LOGGER.info("[Tropeles] {} pasa de {} a {}{}: {}", villager.getName().getString(), from.palabra(), to.palabra(),
                quienNombre.isEmpty() || to == Modo.LIBRE ? "" : " (" + quienNombre + ")", why);
    }

    /** Lets go of what the mode it leaves held: the crouch, the companion's steps, the sweep. */
    private void leave(VillagerEntityMCA villager, Modo from, Modo to) {
        if (from == Modo.ALERTA && to != Modo.ALERTA && agachado) {
            agachado = false;
            porQue = "";
            crouch(villager, false);
        }
        if (from == Modo.SIGUIENDO && to != Modo.SIGUIENDO && villager.getWorld() instanceof ServerWorld world) {
            follow.release(world, villager);
        }
        if (from == Modo.ESPERANDO || to == Modo.ALERTA) {
            mirada.stop();
        }
        if (from == Modo.CONOCIENDO && to != Modo.CONOCIENDO) {
            Companero.terminar(villager);
        }
        if (encargo != null && to != Modo.ENCARGO && to != Modo.ALERTA && villager.getWorld() instanceof ServerWorld world) {
            // told something else ("sígueme", "quédate", "haz lo tuyo"): the errand is dropped
            ProspectingTask.of(villager).dejarEncargo(world, villager);
            MCA.LOGGER.info("[Tropeles] {} deja el encargo de {} ({})", villager.getName().getString(), encargo.paraNombre, encargo.tipo.palabra);
            encargo = null;
            tras = Modo.LIBRE;
            trasQuien = null;
            trasEspera = null;
        }
        if (from == Modo.SIGUIENDO && to != Modo.SIGUIENDO && to != Modo.ALERTA) {
            entrega = null;
        }
        if (from == Modo.OBRA && to != Modo.OBRA && villager.getWorld() instanceof ServerWorld world) {
            // hands and legs off the house; told something else, it stays paused until they say to go on with it
            Constructor c = Constructor.si(villager);
            if (c != null) {
                c.soltar(world, villager, ProspectingTask.of(villager));
            }
            Obra o = ObrasMundo.get(world).de(villager.getUuid());
            if (o != null && to != Modo.ALERTA && !pausaInterna && !o.pausaJugador) {
                o.pausaJugador = true;
                ObrasMundo.get(world).cambio();
                MCA.LOGGER.info("[Tropeles] {} deja la casa en pausa ({})", villager.getName().getString(), to.palabra());
            }
        }
        if (from == Modo.ALERTA || from == Modo.ESPERANDO) {
            if (enemigo != null) {
                CombatMind.disengage(villager);
            }
        }
    }

    /** Back to the mode an alert interrupted, with its player and spot. */
    private Modo resume(VillagerEntityMCA villager, String why) {
        Modo back = debajo;
        UUID who = debajoQuien;
        BlockPos spot = debajoEspera;
        PlayerEntity player = null;
        if (who != null && who.equals(quien) && villager.getWorld() instanceof ServerWorld world) {
            player = player(world);
        } else if (who != null && villager.getServer() != null) {
            player = villager.getServer().getPlayerManager().getPlayer(who);
        }
        change(villager, back, player, spot, why);
        if (back != Modo.LIBRE && player == null && who != null) {
            // the one it was with is not here right now: it keeps to them, and waits for them to show up
            quien = who;
            quienRef = new WeakReference<>(null);
        }
        return back;
    }

    private void save(VillagerEntityMCA villager) {
        Modo kept = modo == Modo.ALERTA ? debajo : modo;
        UUID who = modo == Modo.ALERTA ? debajoQuien : quien;
        BlockPos spot = modo == Modo.ALERTA ? debajoEspera : espera;
        if (kept == Modo.ENCARGO) {
            // a relog ends the errand: it is back to what it was doing for them
            kept = tras;
            who = trasQuien;
            spot = trasEspera;
        }
        String text = switch (kept) {
            case SIGUIENDO, CONOCIENDO -> who == null ? "" : "SIGUIENDO " + who;
            case ESPERANDO -> spot == null ? "" : "ESPERANDO " + spot.getX() + " " + spot.getY() + " " + spot.getZ() + (who == null ? "" : " " + who);
            default -> "";
        };
        if (!text.equals(villager.getVillagerBrain().getWill())) {
            villager.getVillagerBrain().setWill(text);
        }
    }

    private void load(VillagerEntityMCA villager) {
        String text = villager.getVillagerBrain().getWill();
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            String[] parts = text.trim().split(" ");
            if (parts[0].equals("SIGUIENDO") && parts.length >= 2) {
                modo = Modo.SIGUIENDO;
                quien = UUID.fromString(parts[1]);
            } else if (parts[0].equals("ESPERANDO") && parts.length >= 4) {
                modo = Modo.ESPERANDO;
                espera = new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                quien = parts.length >= 5 ? UUID.fromString(parts[4]) : null;
            }
            if (modo != Modo.LIBRE) {
                MCA.LOGGER.info("[Tropeles] {} recuerda que estaba {}", villager.getName().getString(), modo.palabra());
            }
        } catch (RuntimeException e) {
            modo = Modo.LIBRE;
            quien = null;
            espera = null;
        }
    }

    /** The "pensando" line: the mode, the goal, and why. */
    private void think(VillagerEntityMCA villager) {
        long now = villager.getWorld().getTime();
        String doing = Retina.memory(villager).doing();
        String recent = doing != null && !doing.isBlank() && now - Retina.memory(villager).doingTick() < 200 ? doing : "";
        String who = quienNombre.isEmpty() ? "alguien" : quienNombre;
        PlayerEntity player = villager.getWorld() instanceof ServerWorld world ? player(world) : null;
        String far = player == null ? "" : " (a " + Math.round(villager.distanceTo(player)) + " bloques)";
        pensando = switch (modo) {
            case LIBRE -> "vivo lo mío" + (recent.isEmpty() ? "" : ": " + recent);
            case SIGUIENDO -> player == null ? "espero a que " + who + " vuelva, no lo veo por ningún lado"
                    : "sigo a " + who + far + " y lo cuido" + (enemigo != null || CombatMind.busy(villager) ? "; peleo para protegerlo" : "")
                    + (recent.isEmpty() || recent.startsWith("siguiendo") ? "" : "; " + recent);
            case ALERTA -> enemigo != null ? "me defiendo de " + enemigo.getName().getString() + " mientras espero a ver qué pasa"
                    : "me detuve porque " + who + " me avisó; quieto mirándolo" + far + (agachado ? ", agachado porque " + porQue : "")
                    + ", espero a que me diga qué pasa (luego " + (debajo == Modo.LIBRE ? "vuelvo a lo mío" : debajo.palabra()) + ")";
            case CONOCIENDO -> enemigo != null ? "me defiendo de " + enemigo.getName().getString() + " antes de terminar de conocer a " + who
                    : "conozco a " + who + ": lo miré de pies a cabeza, lo saludé y le pregunté su nombre; miro el lugar mientras me contesta";
            case ESPERANDO -> enemigo != null ? "me defiendo de " + enemigo.getName().getString() + " mientras espero"
                    : "espero aquí a " + who + " porque me lo pidió; miro alrededor y me cuido";
            case OBRA -> enemigo != null ? "me defiendo de " + enemigo.getName().getString() + " y luego sigo con la casa"
                    : "construyo la casa de " + who + " con mis manos: " + (Constructor.si(villager) == null ? "la retomo" : Constructor.si(villager).haciendo(true))
                    + "; después " + (obraTras == Modo.SIGUIENDO ? "vuelvo con él" : obraTras == Modo.ESPERANDO ? "vuelvo a esperar donde estaba" : "sigo con lo mío");
            case ENCARGO -> enemigo != null ? "me defiendo de " + enemigo.getName().getString() + " y luego sigo con lo que me pidieron"
                    : encargo == null ? "termino lo que me pidieron" : encargo.pensando() + (recent.isEmpty() ? "" : "; " + recent)
                    + "; después " + (tras == Modo.SIGUIENDO ? "vuelvo con él" : tras == Modo.ESPERANDO ? "vuelvo a esperar donde estaba" : "sigo con lo mío");
        };
    }
}
