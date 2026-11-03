package net.mca.entity.ai.nucleo;

import net.mca.entity.ai.chatAI.Acciones;
import net.mca.entity.ai.nucleo.Episodio.Estado;
import net.mca.entity.ai.nucleo.Episodio.Ser;
import net.mca.entity.ai.nucleo.Episodio.Tipo;
import net.mca.entity.ai.relationship.Personality;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * One villager's inner life, kept with the world ({@link NucleoMundo}): its journal of incidents ({@link Episodio}),
 * the beliefs it drew from them ({@link Hecho}), five traits drawn once from the world seed and its id (how much it
 * cares for others, how much it fears risk, how much it believes what it hears, how much it seeks company, how hard
 * bad things hit it), eight drives as errors between 0 and 1, and the four things that hold its attention.
 */
public final class Mente {
    static final int MAX_EPISODIOS = 400;
    static final int MAX_HECHOS = 96;
    private static final int LEDGER = 96;

    public static final int HAMBRE = 0;
    public static final int CANSANCIO = 1;
    public static final int SEGURIDAD = 2;
    public static final int AFILIACION = 3;
    public static final int DEBER = 4;
    public static final int CURIOSIDAD = 5;
    public static final int DUELO = 6;
    public static final int DOLOR = 7;
    static final String[] IMPULSOS_ES = {"hambre", "cansancio", "miedo", "soledad", "deber", "curiosidad", "duelo", "dolor"};

    /** What holds its attention, how strongly, since when (for the age penalty). */
    record Foco(String clave, String es, String en, float peso, long desde) {
    }

    float afiliacion = 0.5F;
    float riesgo = 0.5F;
    float credulidad = 0.5F;
    float gregarismo = 0.5F;
    float neuroticismo = 0.5F;
    boolean rasgos;

    final float[] impulsos = new float[8];
    @Nullable
    Ser dueloPor;
    long dolorHasta;
    long ultimoSocial;
    long ultimoHallazgo;
    String amenaza = "";
    long amenazaT;

    final ArrayList<Episodio> episodios = new ArrayList<>();
    int siguiente = 1;
    final LinkedHashMap<String, Hecho> hechos = new LinkedHashMap<>();

    final List<Foco> foco = new ArrayList<>(4);
    @Nullable
    Foco atencion;

    /** Origins already told to the one who did it. */
    final LinkedHashSet<String> dichos = new LinkedHashSet<>();
    /** Hearts already moved by hearsay, per origin and player ("origen>uuid"), to move them once and undo them if false. */
    final LinkedHashMap<String, Integer> aplicados = new LinkedHashMap<>();
    /** Hearts for players it has no memories of yet, given when it next meets them. */
    final LinkedHashMap<UUID, Integer> pendientes = new LinkedHashMap<>();

    long ultimoDia = -1;
    long ultimaConsolidacion;
    long dormidoDesde = -1;
    int nuevos;

    /** The question it asked a player about a rumour, waiting for a yes or a no. */
    @Nullable
    UUID preguntaA;
    String preguntaOrigen = "";
    long preguntaHasta;
    /** The words of that question (not saved): anything else it says to them afterwards closes it. */
    String preguntaLinea = "";

    // not saved: when it next does something with others, and whom it is comforting
    long proximoEncuentro;
    long proximoAviso;
    long proximoContar;
    long proximoConsuelo;
    long proximoPendientes;
    @Nullable
    UUID consolando;
    long consolandoHasta;
    boolean consolandoDijo;
    final Map<UUID, Long> yaConsolado = new HashMap<>();

    long tocado;
    @Nullable
    NucleoMundo mundo;

    Mente() {
    }

    void sucio(long now) {
        tocado = now;
        if (mundo != null) {
            mundo.markDirty();
        }
    }

    // ------------------------------------------------------------------ traits

    /** Drawn once, from the world seed and its id, leaning the way its personality leans; then they only drift a little. */
    void rasgos(long seed, UUID id, Personality p) {
        if (rasgos) {
            return;
        }
        Random r = new Random(seed ^ id.getMostSignificantBits() * 31L ^ id.getLeastSignificantBits());
        afiliacion = rasgo(r);
        riesgo = rasgo(r);
        credulidad = rasgo(r);
        gregarismo = rasgo(r);
        neuroticismo = rasgo(r);
        switch (p) {
            case FRIENDLY -> {
                afiliacion += 0.25F;
                gregarismo += 0.1F;
            }
            case SHY -> {
                gregarismo -= 0.3F;
                riesgo += 0.1F;
            }
            case GLOOMY -> neuroticismo += 0.3F;
            case SENSITIVE -> {
                neuroticismo += 0.25F;
                afiliacion += 0.1F;
            }
            case GRUMPY -> {
                afiliacion -= 0.25F;
                credulidad -= 0.15F;
            }
            case PEPPY -> {
                gregarismo += 0.3F;
                neuroticismo -= 0.1F;
            }
            case CONFIDENT -> {
                riesgo -= 0.25F;
                neuroticismo -= 0.1F;
            }
            case ODD -> credulidad += 0.25F;
            case ATHLETIC -> riesgo -= 0.15F;
            case FLIRTY -> gregarismo += 0.2F;
            case WITTY -> credulidad -= 0.1F;
            case GREEDY -> afiliacion -= 0.15F;
            case LAZY -> riesgo += 0.05F;
            default -> {
            }
        }
        acotar();
        rasgos = true;
    }

    private static float rasgo(Random r) {
        return 0.5F + (float) r.nextGaussian() * 0.15F;
    }

    void acotar() {
        afiliacion = clamp(afiliacion, 0.05F, 0.95F);
        riesgo = clamp(riesgo, 0.05F, 0.95F);
        credulidad = clamp(credulidad, 0.05F, 0.95F);
        gregarismo = clamp(gregarismo, 0.05F, 0.95F);
        neuroticismo = clamp(neuroticismo, 0.05F, 0.95F);
    }

    // ------------------------------------------------------------------ the journal

    Episodio nuevo(Tipo tipo, long now, UUID yo) {
        int id = siguiente++;
        return new Episodio(id, tipo, now, yo.toString().substring(0, 8) + ":" + id);
    }

    /**
     * Into the journal: a repeat within the kind's window folds into the same open incident ("me pegó 3 veces"), else
     * it is a new one, weighed by salience = 0.45|valence| + 0.25 novelty + 0.20 drive pressure + 0.10 social, with
     * bad things weighing more on the anxious. Returns the episode kept (the old one when folded).
     */
    Episodio registrar(Episodio e, long now) {
        Episodio igual = abierto(e, now);
        if (igual != null) {
            igual.veces += e.veces;
            igual.cantidad += e.cantidad;
            igual.t1 = now;
            igual.valencia = clamp(igual.valencia + e.valencia * 0.25F, -1.0F, 1.0F);
            igual.saliencia = Math.min(1.0F, Math.max(igual.saliencia, e.saliencia) + 0.04F);
            if (e.donde != null) {
                igual.donde = e.donde;
                igual.dim = e.dim;
            }
            if (!e.cosa.isEmpty() && e.tipo.ordinal() > Tipo.AVISE.ordinal()) {
                // a running account ("la casa va en 40%") keeps its newest words
                igual.cosa = e.cosa;
                igual.cosaEn = e.cosaEn;
            }
            sucio(now);
            return igual;
        }
        e.saliencia = saliencia(e, now);
        episodios.add(e);
        nuevos++;
        while (episodios.size() > MAX_EPISODIOS) {
            olvidarUno(now);
        }
        sucio(now);
        return e;
    }

    /** Straight into the journal as it is (a heard copy, a summary): no folding, its own salience. */
    void anadir(Episodio e, long now) {
        episodios.add(e);
        nuevos++;
        while (episodios.size() > MAX_EPISODIOS) {
            olvidarUno(now);
        }
        sucio(now);
    }

    @Nullable
    private Episodio abierto(Episodio e, long now) {
        if (e.tipo.ventana <= 0) {
            return null;
        }
        for (int i = episodios.size() - 1, seen = 0; i >= 0 && seen < 24; i--, seen++) {
            Episodio o = episodios.get(i);
            if (o.tipo == e.tipo && o.estado == Estado.ABIERTO && o.modo == e.modo && now - o.t1 <= e.tipo.ventana
                    && mismo(o.quien, e.quien) && mismo(o.aQuien, e.aQuien)) {
                return o;
            }
        }
        return null;
    }

    static boolean mismo(@Nullable Ser a, @Nullable Ser b) {
        return a == null ? b == null : a.mismo(b);
    }

    private float saliencia(Episodio e, long now) {
        if (e.tipo == Tipo.MUERTE) {
            return 1.0F;
        }
        float novedad = 1.0F;
        String lugar = e.lugar();
        for (int i = episodios.size() - 1, seen = 0; i >= 0 && seen < 64; i--, seen++) {
            Episodio o = episodios.get(i);
            if (now - o.t1 <= 24000 && o.tipo == e.tipo && mismo(o.quien, e.quien) && o.lugar().equals(lugar)) {
                novedad = 0.2F;
                break;
            }
        }
        float presion = 0;
        for (float d : impulsos) {
            presion = Math.max(presion, d);
        }
        float social = e.quien != null && e.quien.persona() ? 1.0F : e.aQuien != null && e.aQuien.persona() ? 0.6F : 0.0F;
        float s = 0.45F * Math.abs(e.valencia) + 0.25F * novedad + 0.20F * presion + 0.10F * social;
        if (e.valencia < 0) {
            s *= 0.8F + 0.4F * neuroticismo;
        }
        return clamp(0.6F * s + 0.4F * e.tipo.saliencia, 0.0F, 1.0F);
    }

    static long celda(@Nullable BlockPos p) {
        return p == null ? Long.MIN_VALUE : BlockPos.asLong(p.getX() >> 4, p.getY() >> 4, p.getZ() >> 4);
    }

    /** Leaves RAM for a long while: only the {@code max} incidents that matter most stay (summaries count double). */
    void compactar(long now, int max) {
        while (episodios.size() > max) {
            olvidarUno(now);
        }
    }

    /** The journal is full: the closed episode that matters least now (salience, certainty, age) goes. */
    private void olvidarUno(long now) {
        int worst = -1;
        double worstScore = Double.MAX_VALUE;
        for (int i = 0; i < episodios.size(); i++) {
            Episodio e = episodios.get(i);
            if (e.estado == Estado.ABIERTO && now - e.t1 < 24000) {
                continue;
            }
            double keep = e.saliencia * (0.4 + 0.6 * e.confianza) * Math.exp(-(now - e.t1) / 168000.0)
                    * (e.estado == Estado.ABSORBIDO ? 0.3 : e.estado == Estado.FALSO ? 0.7 : 1.0) * (e.tipo == Tipo.RESUMEN ? 2.0 : 1.0);
            if (keep < worstScore) {
                worstScore = keep;
                worst = i;
            }
        }
        episodios.remove(worst < 0 ? 0 : worst);
    }

    @Nullable
    Episodio porId(int id) {
        for (int i = episodios.size() - 1; i >= 0; i--) {
            if (episodios.get(i).id == id) {
                return episodios.get(i);
            }
        }
        return null;
    }

    @Nullable
    Episodio porOrigen(String origen) {
        for (int i = episodios.size() - 1; i >= 0; i--) {
            if (episodios.get(i).origen.equals(origen)) {
                return episodios.get(i);
            }
        }
        return null;
    }

    /** The last one of a kind (optionally about someone), or null. */
    @Nullable
    Episodio ultimo(Tipo tipo, @Nullable UUID sobre) {
        for (int i = episodios.size() - 1; i >= 0; i--) {
            Episodio e = episodios.get(i);
            if (e.tipo == tipo && e.estado != Estado.ABSORBIDO && (sobre == null || e.sobre(sobre))) {
                return e;
            }
        }
        return null;
    }

    /**
     * Recall: the {@code k} episodes that best fit now, scored 0.25 recency (e^(-dt/48000)) + 0.35 importance +
     * 0.40 relevance; relevance is being about {@code sobre}, or sharing words with {@code palabras}.
     */
    List<Episodio> recordar(long now, @Nullable UUID sobre, @Nullable String palabras, int k) {
        String[] words = palabras == null || palabras.isBlank() ? new String[0] : Acciones.normalize(palabras).split("[^a-z0-9]+");
        List<Map.Entry<Episodio, Double>> scored = new ArrayList<>();
        for (Episodio e : episodios) {
            if (e.estado == Estado.ABSORBIDO || e.tipo == Tipo.DIJE) {
                continue;
            }
            double rec = Math.exp(-(now - e.t1) / 48000.0);
            double imp = e.saliencia * (0.5 + 0.5 * e.confianza) * (e.estado == Estado.FALSO ? 0.5 : 1.0);
            double rel = 0;
            if (e.sobre(sobre)) {
                rel = 1;
            } else if (words.length > 0) {
                String t = Acciones.normalize(e.narrar(true));
                int hits = 0;
                for (String w : words) {
                    if (w.length() > 3 && t.contains(w)) {
                        hits++;
                    }
                }
                rel = Math.min(1.0, hits * 0.5);
            }
            scored.add(Map.entry(e, 0.25 * rec + 0.35 * imp + 0.40 * rel));
        }
        scored.sort(Map.Entry.<Episodio, Double>comparingByValue().reversed());
        List<Episodio> out = new ArrayList<>(k);
        for (int i = 0; i < Math.min(k, scored.size()); i++) {
            out.add(scored.get(i).getKey());
        }
        return out;
    }

    // ------------------------------------------------------------------ beliefs

    Hecho hecho(String clave, String valor, long now) {
        Hecho h = hechos.get(clave);
        if (h == null) {
            h = new Hecho(clave, valor, now);
            hechos.put(clave, h);
            while (hechos.size() > MAX_HECHOS) {
                String weakest = null;
                float w = Float.MAX_VALUE;
                for (Hecho o : hechos.values()) {
                    float s = o.soporte() * o.peso(now);
                    if (o != h && s < w) {
                        w = s;
                        weakest = o.clave;
                    }
                }
                if (weakest == null) {
                    break;
                }
                hechos.remove(weakest);
            }
        } else if (!valor.isEmpty()) {
            h.valor = valor;
        }
        return h;
    }

    /** How good it believes someone is, 0..1 (0.5 when it knows nothing). */
    float trato(@Nullable UUID who) {
        Hecho h = who == null ? null : hechos.get("trato:" + who);
        return h == null ? 0.5F : h.creencia();
    }

    List<Hecho> hechosFuertes(long now, int n) {
        List<Hecho> all = new ArrayList<>(hechos.values());
        all.removeIf(h -> h.soporte() * h.peso(now) < 0.5F);
        all.sort(Comparator.comparingDouble((Hecho h) -> h.soporte() * h.peso(now)).reversed());
        return all.subList(0, Math.min(n, all.size()));
    }

    static <K, V> void limitar(Map<K, V> map) {
        Iterator<K> it = map.keySet().iterator();
        while (map.size() > LEDGER && it.hasNext()) {
            it.next();
            it.remove();
        }
    }

    static void limitar(LinkedHashSet<String> set) {
        Iterator<String> it = set.iterator();
        while (set.size() > LEDGER && it.hasNext()) {
            it.next();
            it.remove();
        }
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    // ------------------------------------------------------------------ saved

    NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putBoolean("r", rasgos);
        n.putFloat("ra", afiliacion);
        n.putFloat("rr", riesgo);
        n.putFloat("rc", credulidad);
        n.putFloat("rg", gregarismo);
        n.putFloat("rn", neuroticismo);
        n.putFloat("duelo", impulsos[DUELO]);
        if (dueloPor != null) {
            dueloPor.escribir(n, "dp");
        }
        n.putLong("us", ultimoSocial);
        n.putLong("uh", ultimoHallazgo);
        n.putInt("sig", siguiente);
        NbtList eps = new NbtList();
        for (Episodio e : episodios) {
            eps.add(e.toNbt());
        }
        n.put("ep", eps);
        NbtList hs = new NbtList();
        for (Hecho h : hechos.values()) {
            hs.add(h.toNbt());
        }
        n.put("he", hs);
        NbtCompound dc = new NbtCompound();
        int i = 0;
        for (String s : dichos) {
            dc.putString(String.valueOf(i++), s);
        }
        n.put("di", dc);
        NbtCompound ap = new NbtCompound();
        aplicados.forEach(ap::putInt);
        n.put("ap", ap);
        NbtCompound pe = new NbtCompound();
        pendientes.forEach((k, v) -> pe.putInt(k.toString(), v));
        n.put("pe", pe);
        n.putLong("dia", ultimoDia);
        n.putLong("uc", ultimaConsolidacion);
        n.putInt("nu", nuevos);
        if (preguntaA != null) {
            n.putUuid("pa", preguntaA);
            n.putString("po", preguntaOrigen);
            n.putLong("ph", preguntaHasta);
        }
        n.putLong("tocado", tocado);
        return n;
    }

    static Mente fromNbt(NbtCompound n) {
        Mente m = new Mente();
        m.rasgos = n.getBoolean("r");
        m.afiliacion = n.getFloat("ra");
        m.riesgo = n.getFloat("rr");
        m.credulidad = n.getFloat("rc");
        m.gregarismo = n.getFloat("rg");
        m.neuroticismo = n.getFloat("rn");
        m.impulsos[DUELO] = n.getFloat("duelo");
        m.dueloPor = Ser.leer(n, "dp");
        m.ultimoSocial = n.getLong("us");
        m.ultimoHallazgo = n.getLong("uh");
        m.siguiente = Math.max(1, n.getInt("sig"));
        NbtList eps = n.getList("ep", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < eps.size(); i++) {
            Episodio e = Episodio.fromNbt(eps.getCompound(i));
            m.episodios.add(e);
            m.siguiente = Math.max(m.siguiente, e.id + 1);
        }
        NbtList hs = n.getList("he", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < hs.size(); i++) {
            Hecho h = Hecho.fromNbt(hs.getCompound(i));
            m.hechos.put(h.clave, h);
        }
        NbtCompound dc = n.getCompound("di");
        for (int i = 0; dc.contains(String.valueOf(i)); i++) {
            m.dichos.add(dc.getString(String.valueOf(i)));
        }
        NbtCompound ap = n.getCompound("ap");
        for (String k : ap.getKeys()) {
            m.aplicados.put(k, ap.getInt(k));
        }
        NbtCompound pe = n.getCompound("pe");
        for (String k : pe.getKeys()) {
            try {
                m.pendientes.put(UUID.fromString(k), pe.getInt(k));
            } catch (IllegalArgumentException ignored) {
                // not a player id
            }
        }
        m.ultimoDia = n.contains("dia") ? n.getLong("dia") : -1;
        m.ultimaConsolidacion = n.getLong("uc");
        m.nuevos = n.getInt("nu");
        if (n.containsUuid("pa")) {
            m.preguntaA = n.getUuid("pa");
            m.preguntaOrigen = n.getString("po");
            m.preguntaHasta = n.getLong("ph");
        }
        m.tocado = n.getLong("tocado");
        return m;
    }
}
