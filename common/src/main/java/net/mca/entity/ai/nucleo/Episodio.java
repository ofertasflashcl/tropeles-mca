package net.mca.entity.ai.nucleo;

import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.mente.Iniciativa;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One incident in a villager's journal: the event, never the frames it was seen in. "Benja me pegó 3 veces en
 * 102 64 -31" is one episode however many hits it took ({@link Mente#registrar} folds repeats within the kind's
 * window into it). It knows how it is known ({@link Modo}: lived or seen, heard from someone, worked out), how sure
 * it is ({@link #confianza}), how much it matters ({@link #saliencia}), how it felt ({@link #valencia}, -1..1), what
 * caused it ({@link #causas}, ids in the same mind) and where it first came from ({@link #origen}, the same in every
 * mind it travels to, so a rumour heard twice is one rumour told by two people). A falsified one is kept, marked.
 */
public final class Episodio {
    /** Kinds of incident: how good or bad they feel, how much they matter, how long a repeat folds into the same one. */
    public enum Tipo {
        HERIDO(-0.6F, 0.55F, 200, true, "golpe"),
        VIO_HERIR(-0.5F, 0.5F, 200, true, "agresión vista"),
        MUERTE(-1.0F, 1.0F, 0, true, "muerte"),
        REGALO(0.5F, 0.4F, 1200, true, "regalo"),
        ROBO(-0.7F, 0.65F, 2400, true, "robo"),
        CONSOLE(0.45F, 0.4F, 2400, true, "consuelo"),
        /** Someone asked it to forgive them: words, not deeds, so it eases a grudge and builds no trust ({@link Vinculo}). */
        PERDON(0.2F, 0.3F, 600, false, "disculpa"),
        HUI(-0.35F, 0.3F, 400, false, "huida"),
        AVISE(0.2F, 0.25F, 600, false, "aviso"),
        PELEA(-0.25F, 0.3F, 1200, false, "pelea"),
        HALLAZGO(0.3F, 0.25F, 1200, false, "hallazgo"),
        CUMPLI(0.4F, 0.3F, 0, false, "encargo cumplido"),
        DI(0.3F, 0.25F, 600, false, "entrega"),
        OBRA(0.4F, 0.3F, 6000, false, "obra"),
        DIJE(0.0F, 0.2F, 0, false, "dicho"),
        REFLEXION(0.0F, 0.45F, 0, false, "reflexión"),
        RESUMEN(0.0F, 0.35F, 0, false, "resumen");

        public final float valencia;
        public final float saliencia;
        public final int ventana;
        /** Worth telling others: something that happened between people. */
        public final boolean contable;
        public final String palabra;

        Tipo(float valencia, float saliencia, int ventana, boolean contable, String palabra) {
            this.valencia = valencia;
            this.saliencia = saliencia;
            this.ventana = ventana;
            this.contable = contable;
            this.palabra = palabra;
        }

        static Tipo de(String name) {
            try {
                return valueOf(name);
            } catch (IllegalArgumentException e) {
                return RESUMEN;
            }
        }
    }

    /** How it knows: it lived or saw it, someone told it, or it worked it out from what it found. */
    public enum Modo { VISTO, OIDO, DEDUCIDO }

    public enum Estado { ABIERTO, CERRADO, FALSO, ABSORBIDO }

    public static final char JUGADOR = 'j';
    public static final char ALDEANO = 'a';
    public static final char BICHO = 'm';
    public static final char OTRO = 'o';

    /** Someone who took part: a player, a villager, a creature. Null in an episode means the one who remembers it. */
    public record Ser(@Nullable UUID id, String es, String en, char clase) {
        public static Ser de(Entity e) {
            if (e instanceof PlayerEntity || e instanceof VillagerEntityMCA) {
                String n = e.getName().getString();
                return new Ser(e.getUuid(), n, n, e instanceof PlayerEntity ? JUGADOR : ALDEANO);
            }
            String[] n = Episodio.nombre(e.getType());
            return new Ser(e.getUuid(), n[0], n[1], e.getType().getSpawnGroup() == SpawnGroup.MONSTER ? BICHO : OTRO);
        }

        public boolean persona() {
            return clase == JUGADOR || clase == ALDEANO;
        }

        public String nombre(boolean spanish) {
            return spanish ? es : en;
        }

        /** The same one: by id for people, by kind for creatures (every zombie is "un zombi"). */
        public boolean mismo(@Nullable Ser o) {
            return o != null && clave().equals(o.clave());
        }

        public boolean es(@Nullable UUID other) {
            return id != null && id.equals(other);
        }

        public String clave() {
            return persona() && id != null ? id.toString() : clase + ":" + es;
        }

        void escribir(NbtCompound nbt, String k) {
            NbtCompound s = new NbtCompound();
            if (id != null) {
                s.putUuid("id", id);
            }
            s.putString("es", es);
            if (!en.equals(es)) {
                s.putString("en", en);
            }
            s.putString("c", String.valueOf(clase));
            nbt.put(k, s);
        }

        @Nullable
        static Ser leer(NbtCompound nbt, String k) {
            if (!nbt.contains(k)) {
                return null;
            }
            NbtCompound s = nbt.getCompound(k);
            String es = s.getString("es");
            String c = s.getString("c");
            return new Ser(s.containsUuid("id") ? s.getUuid("id") : null, es, s.contains("en") ? s.getString("en") : es,
                    c.isEmpty() ? OTRO : c.charAt(0));
        }
    }

    public final int id;
    public final Tipo tipo;
    public long t0;
    public long t1;
    public int veces = 1;
    public float cantidad;
    @Nullable
    public Ser quien;
    @Nullable
    public Ser aQuien;
    public String cosa = "";
    public String cosaEn = "";
    @Nullable
    public BlockPos donde;
    /** The dimension of {@link #donde} ("" when unknown, as in saves from before it was kept). */
    public String dim = "";
    public float valencia;
    public float saliencia;
    public float confianza = 1.0F;
    public Modo modo = Modo.VISTO;
    @Nullable
    public Ser fuente;
    /** Everyone this account came through (the teller and the teller's own sources): hearsay along one chain is one piece of evidence. */
    final List<String> narradores = new ArrayList<>(2);
    public int saltos;
    /** Independent tellers who said the same thing (corroboration). */
    public int apoyos = 1;
    public Estado estado = Estado.ABIERTO;
    public int[] causas = new int[0];
    public final String origen;
    /** How many others it has told this to. */
    public int contado;
    /** Already weighed into its beliefs at a consolidation. */
    boolean digerido;
    /** The summary that holds it (0: none). */
    int resumen;
    /** Who else saw it happen (not saved): their copy is folded together with this one while it goes on. */
    final transient List<UUID> testigos = new ArrayList<>(2);

    Episodio(int id, Tipo tipo, long t, String origen) {
        this.id = id;
        this.tipo = tipo;
        this.t0 = t;
        this.t1 = t;
        this.origen = origen;
        this.valencia = tipo.valencia;
        this.saliencia = tipo.saliencia;
    }

    public boolean vivo() {
        return estado == Estado.ABIERTO || estado == Estado.CERRADO;
    }

    /** About someone, as doer or as the one it was done to. */
    public boolean sobre(@Nullable UUID who) {
        return who != null && (quien != null && quien.es(who) || aQuien != null && aQuien.es(who));
    }

    /** Where it happened, in which dimension. */
    public Episodio en(@Nullable World world, @Nullable BlockPos p) {
        donde = p == null ? null : p.toImmutable();
        dim = world == null ? "" : world.getRegistryKey().getValue().toString();
        return this;
    }

    /** The place as a key: the same cell in another dimension is another place. */
    String lugar() {
        return dim + "@" + Mente.celda(donde);
    }

    /** "102 64 -31", and the dimension when it is not the overworld. */
    public String dondeTexto(boolean es) {
        if (donde == null) {
            return "";
        }
        String xyz = donde.getX() + " " + donde.getY() + " " + donde.getZ();
        return switch (dim) {
            case "", "minecraft:overworld" -> xyz;
            case "minecraft:the_nether" -> xyz + (es ? " en el Nether" : " in the Nether");
            case "minecraft:the_end" -> xyz + (es ? " en el End" : " in the End");
            default -> xyz + (es ? " en " : " in ") + dim;
        };
    }

    /** This account already came through {@code s}. */
    boolean oyo(Ser s) {
        return fuente != null && fuente.mismo(s) || narradores.contains(s.clave());
    }

    /** Another's account {@code o} came through someone this one came through too: it is no new evidence. */
    boolean comparte(Episodio o) {
        if (o.modo != Modo.OIDO) {
            return false; // a witness's own account is independent
        }
        if (o.fuente != null && oyo(o.fuente)) {
            return true;
        }
        for (String k : o.narradores) {
            if (narradores.contains(k) || fuente != null && fuente.clave().equals(k)) {
                return true;
            }
        }
        return false;
    }

    /** Remembers that {@code teller} told it, and whom the teller had it from. */
    void anotar(Ser teller, Episodio dicho) {
        agregar(teller.clave());
        if (dicho.modo == Modo.OIDO) {
            if (dicho.fuente != null) {
                agregar(dicho.fuente.clave());
            }
            for (String k : dicho.narradores) {
                agregar(k);
            }
        }
    }

    private void agregar(String k) {
        if (!narradores.contains(k)) {
            narradores.add(k);
            while (narradores.size() > 8) {
                narradores.remove(0);
            }
        }
    }

    /** The copy a listener keeps when {@code teller} tells it: in the third person, weaker, from that source. */
    Episodio oido(int nuevo, Ser teller, float credibilidad) {
        Episodio c = new Episodio(nuevo, tipo, t0, origen);
        c.t1 = t1;
        c.veces = veces;
        c.cantidad = cantidad;
        // "me" in the teller's memory is the teller in the listener's; a death's unknown killer stays unknown
        c.quien = quien == null && tipo == Tipo.CONSOLE ? teller : quien;
        c.aQuien = aQuien == null && tipo != Tipo.MUERTE ? teller : aQuien;
        c.cosa = cosa;
        c.cosaEn = cosaEn;
        c.donde = donde;
        c.dim = dim;
        c.valencia = valencia * 0.7F;
        c.saliencia = saliencia * 0.8F;
        c.confianza = Math.max(0.05F, Math.min(1.0F, confianza * credibilidad));
        c.modo = Modo.OIDO;
        c.fuente = teller;
        c.anotar(teller, this);
        c.saltos = saltos + 1;
        c.estado = Estado.CERRADO;
        return c;
    }

    // ------------------------------------------------------------------ in words

    /** What happened, told by the one who remembers it: "Benja me pegó (3 veces)", "vi a Benja pegarle a Rosa". */
    public String narrar(boolean es) {
        String q = quien == null ? "" : quien.nombre(es);
        String a = aQuien == null ? "" : aQuien.nombre(es);
        String c = es || cosaEn.isEmpty() ? cosa : cosaEn;
        String veces = this.veces > 1 ? (es ? " (" + this.veces + " veces)" : " (" + this.veces + " times)") : "";
        String s = switch (tipo) {
            case HERIDO, VIO_HERIR -> modo == Modo.VISTO && aQuien != null
                    ? (es ? "vi a " + q + " pegarle a " + a : "I saw " + q + " hit " + a)
                    : aQuien == null ? (es ? q + " me pegó" : q + " hit me") : (es ? q + " le pegó a " + a : q + " hit " + a);
            case MUERTE -> modo == Modo.VISTO
                    ? (es ? "vi morir a " + a + (quien == null ? "" : " a manos de " + q) : "I saw " + a + " die" + (quien == null ? "" : " at the hands of " + q))
                    : (es ? a + " murió" + (quien == null ? "" : ", lo mató " + q) : a + " died" + (quien == null ? "" : ", killed by " + q));
            case REGALO -> aQuien == null ? (es ? q + " me regaló " + c : q + " gave me " + c) : (es ? q + " le regaló " + c + " a " + a : q + " gave " + a + " " + c);
            case ROBO -> aQuien == null
                    ? (es ? q + " me robó " + cuantas(true) + " del cofre" : q + " stole " + cuantas(false) + " from my chest")
                    : (es ? q + " le robó " + cuantas(true) + " a " + a : q + " stole " + cuantas(false) + " from " + a);
            case CONSOLE -> quien == null ? (es ? "acompañé a " + a + " en su pena" : "I kept " + a + " company in their grief")
                    : aQuien == null ? (es ? q + " me acompañó en mi pena" : q + " kept me company in my grief")
                    : (es ? q + " acompañó a " + a + " en su pena" : q + " kept " + a + " company in their grief");
            case HUI -> (es ? "huí de " + q : "I ran from " + q) + (c.isEmpty() ? "" : es ? " porque " + c : " because " + c);
            case PERDON -> es ? q + " me pidió perdón" : q + " said sorry to me";
            case AVISE -> es ? "le avisé a " + a + " de " + q : "I warned " + a + " about " + q;
            default -> c;
        };
        return tipo == Tipo.HUI || tipo == Tipo.MUERTE || tipo.ordinal() > Tipo.AVISE.ordinal() ? s : s + veces;
    }

    /** Said to the one who did it: "le pegaste a Rosa", "me regalaste pan". */
    public String narrarATi(boolean es) {
        String a = aQuien == null ? "" : aQuien.nombre(es);
        String c = es || cosaEn.isEmpty() ? cosa : cosaEn;
        return switch (tipo) {
            case HERIDO, VIO_HERIR -> aQuien == null ? (es ? "me pegaste" : "you hit me") : (es ? "le pegaste a " + a : "you hit " + a);
            case MUERTE -> es ? "mataste a " + a : "you killed " + a;
            case REGALO -> aQuien == null ? (es ? "me regalaste " + c : "you gave me " + c) : (es ? "le regalaste " + c + " a " + a : "you gave " + a + " " + c);
            case ROBO -> aQuien == null ? (es ? "me robaste del cofre" : "you stole from my chest") : (es ? "le robaste a " + a : "you stole from " + a);
            case CONSOLE -> es ? "acompañaste a " + a + " en su pena" : "you kept " + a + " company in their grief";
            case PERDON -> es ? "me pediste perdón" : "you said sorry";
            default -> narrar(es);
        };
    }

    private String cuantas(boolean es) {
        int n = Math.max(1, Math.round(cantidad));
        return es ? n + (n == 1 ? " cosa" : " cosas") : n + (n == 1 ? " thing" : " things");
    }

    /** How it knows: "lo vi", "me lo contó Luz", "lo deduje". */
    public String como(boolean es) {
        return switch (modo) {
            case VISTO -> aQuien == null || tipo == Tipo.VIO_HERIR || tipo == Tipo.MUERTE ? (es ? "lo viví" : "it happened to me")
                    : (es ? "lo vi" : "I saw it");
            case OIDO -> fuente == null ? (es ? "me enteré" : "I heard")
                    : (es ? "me lo contó " + fuente.es + (saltos > 1 ? ", que lo oyó de otro" : "") : fuente.en + " told me" + (saltos > 1 ? " (second-hand)" : ""));
            case DEDUCIDO -> es ? "lo deduje" : "I worked it out";
        };
    }

    /** When, from now: "recién", "hace unos minutos", "hoy", "ayer", "hace 3 días". */
    public static String cuando(long dt, boolean es) {
        if (dt < 1200) {
            return es ? "recién" : "just now";
        }
        if (dt < 6000) {
            return es ? "hace unos minutos" : "a few minutes ago";
        }
        if (dt < 24000) {
            return es ? "hoy" : "today";
        }
        if (dt < 48000) {
            return es ? "ayer" : "yesterday";
        }
        long days = dt / 24000;
        return es ? "hace " + days + " días" : days + " days ago";
    }

    /** "un zombi" / "a zombie"; a few peaceful creatures by name, "algo" for the rest. */
    static String[] nombre(EntityType<?> type) {
        if (type.getSpawnGroup() == SpawnGroup.MONSTER) {
            return Iniciativa.monsterName(type);
        }
        String en = type.getName().getString().toLowerCase(java.util.Locale.ROOT);
        String es = switch (Registries.ENTITY_TYPE.getId(type).getPath()) {
            case "iron_golem" -> "un gólem de hierro";
            case "wolf" -> "un lobo";
            case "bee" -> "una abeja";
            case "llama", "trader_llama" -> "una llama";
            case "polar_bear" -> "un oso polar";
            case "goat" -> "una cabra";
            case "panda" -> "un panda";
            case "fox" -> "un zorro";
            case "dolphin" -> "un delfín";
            case "arrow" -> "una flecha";
            default -> "algo";
        };
        return new String[]{es, (!en.isEmpty() && "aeiou".indexOf(en.charAt(0)) >= 0 ? "an " : "a ") + en};
    }

    // ------------------------------------------------------------------ saved

    NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putInt("i", id);
        n.putString("t", tipo.name());
        n.putLong("t0", t0);
        n.putLong("t1", t1);
        if (veces != 1) {
            n.putInt("n", veces);
        }
        if (cantidad != 0) {
            n.putFloat("q", cantidad);
        }
        if (quien != null) {
            quien.escribir(n, "w");
        }
        if (aQuien != null) {
            aQuien.escribir(n, "a");
        }
        if (!cosa.isEmpty()) {
            n.putString("c", cosa);
        }
        if (!cosaEn.isEmpty()) {
            n.putString("ce", cosaEn);
        }
        if (donde != null) {
            n.putLong("p", donde.asLong());
        }
        if (!dim.isEmpty()) {
            n.putString("dm", dim);
        }
        n.putFloat("v", valencia);
        n.putFloat("s", saliencia);
        n.putFloat("k", confianza);
        n.putByte("m", (byte) modo.ordinal());
        if (fuente != null) {
            fuente.escribir(n, "f");
        }
        if (!narradores.isEmpty()) {
            NbtList nr = new NbtList();
            narradores.forEach(k -> nr.add(NbtString.of(k)));
            n.put("nr", nr);
        }
        if (saltos != 0) {
            n.putByte("h", (byte) saltos);
        }
        if (apoyos != 1) {
            n.putByte("ap", (byte) Math.min(127, apoyos));
        }
        n.putByte("e", (byte) estado.ordinal());
        if (causas.length > 0) {
            n.putIntArray("x", causas);
        }
        n.putString("o", origen);
        if (contado != 0) {
            n.putByte("ct", (byte) Math.min(127, contado));
        }
        if (digerido) {
            n.putBoolean("dg", true);
        }
        if (resumen != 0) {
            n.putInt("rs", resumen);
        }
        return n;
    }

    static Episodio fromNbt(NbtCompound n) {
        Episodio e = new Episodio(n.getInt("i"), Tipo.de(n.getString("t")), n.getLong("t0"), n.getString("o"));
        e.t1 = n.getLong("t1");
        e.veces = n.contains("n") ? n.getInt("n") : 1;
        e.cantidad = n.getFloat("q");
        e.quien = Ser.leer(n, "w");
        e.aQuien = Ser.leer(n, "a");
        e.cosa = n.getString("c");
        e.cosaEn = n.getString("ce");
        e.donde = n.contains("p") ? BlockPos.fromLong(n.getLong("p")) : null;
        e.valencia = n.getFloat("v");
        e.saliencia = n.getFloat("s");
        e.confianza = n.getFloat("k");
        e.modo = Modo.values()[Math.min(Modo.values().length - 1, Math.max(0, n.getByte("m")))];
        e.fuente = Ser.leer(n, "f");
        e.dim = n.getString("dm");
        NbtList nr = n.getList("nr", NbtElement.STRING_TYPE);
        for (int i = 0; i < nr.size() && i < 8; i++) {
            e.narradores.add(nr.getString(i));
        }
        e.saltos = n.getByte("h");
        e.apoyos = n.contains("ap") ? n.getByte("ap") : 1;
        e.estado = Estado.values()[Math.min(Estado.values().length - 1, Math.max(0, n.getByte("e")))];
        e.causas = n.getIntArray("x");
        e.contado = n.getByte("ct");
        e.digerido = n.getBoolean("dg");
        e.resumen = n.getInt("rs");
        return e;
    }
}
