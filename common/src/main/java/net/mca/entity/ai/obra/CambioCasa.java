package net.mca.entity.ai.obra;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What a player asks of a house proposed to them (C03b), read from their own words, and done to its draft: another
 * design, wood or roof, one or two windows opened or walled up on one wall. Also whether a short answer of theirs is a
 * plain yes or a plain no to it. Pure: no world; the same words on the same draft give the same plan every time.
 */
public final class CambioCasa {
    private CambioCasa() {
    }

    /** A wall of the house, as seen from outside, in front of its door (plan coordinates: the front is to the north, -z). */
    public enum Lado {
        FRENTE("al frente", "at the front"),
        ATRAS("atrás", "at the back"),
        IZQUIERDA("a la izquierda", "on the left"),
        DERECHA("a la derecha", "on the right");

        public final String es;
        public final String en;

        Lado(String es, String en) {
            this.es = es;
            this.en = en;
        }
    }

    /** Windows to open or to wall up: how many, and on which wall (null: wherever they fit best). */
    public record Ventanas(boolean abrir, int cuantas, @Nullable Lado lado) {
    }

    /** What was asked; each part null (or no windows) when it was not. */
    public record Pedido(@Nullable ModelosCasa.Modelo modelo, @Nullable Plano.Madera madera, @Nullable ModelosCasa.Cubierta cubierta,
                         List<Ventanas> ventanas) {
        public Pedido {
            ventanas = List.copyOf(ventanas);
        }

        public boolean vacio() {
            return modelo == null && madera == null && cubierta == null && ventanas.isEmpty();
        }

        /** The roof and the windows only: what is done to a draft once its design and wood are chosen. */
        public Pedido arreglos() {
            return new Pedido(null, null, cubierta, ventanas);
        }
    }

    /**
     * A change done or refused: the draft it was made on and the one it gave, the real difference between their plans,
     * and what was done (Spanish, English); refused, no new draft and why not, and nothing changed.
     */
    public record Hecho(CatalogoPlanos.Borrador antes, @Nullable CatalogoPlanos.Borrador despues, @Nullable CatalogoPlanos.Diferencia diferencia,
                        String es, String en) {
        public boolean ok() {
            return despues != null;
        }
    }

    // ------------------------------------------------------------------ reading what was asked

    private static final Pattern CABANA = w("cabana|cabanita|cabin|hut|log cabin");
    private static final Pattern LARGA = w("casa larga|casa alargada|alargada|la larga|long house|longhouse");
    private static final Pattern DOS_PISOS = w("dos pisos|2 pisos|dos plantas|two floors?|two storeys?|two stories|two story|la de siempre|la primera|la grande");
    private static final Pattern ALTO = w("techo (mas )?(alto|empinado|inclinado|puntiagudo|parado|en punta)|mas alta|steep( roof)?|steeper|tall roof|"
            + "higher roof|pointy roof|pointed roof|taller");
    private static final Pattern TRAVES = w("techo (girado|al reves|cruzado|atravesado|de lado|para el otro lado|al otro lado)|gira(r|le)? el techo|"
            + "turned roof|turn the roof|roof the other way|rotate the roof");
    private static final Pattern DOS_AGUAS = w("dos aguas|techo (normal|bajo|comun|de siempre|como venia|original)|gable roof|normal roof|lower roof|"
            + "regular roof");
    private static final Pattern VENTANA = w("ventanas?|ventanitas?|ventanales?|windows?");
    private static final Pattern PLURAL = w("ventanas|ventanitas|ventanales|windows|las|los");
    private static final Pattern CERRAR = w("cierra|cierrale|cerrar|cerrarle|tapa|tapale|tapar|taparle|tapa(me)?la|quita|quitale|quitar|quitarle|saca|"
            + "sacale|sacar|elimina|eliminale|borra|sin|menos|close|shut|remove|wall up|brick up|block|fewer|less|no quiero");
    private static final Pattern UNA = w("una|un|1|one|otra|another|single");
    /** "y otra a la derecha", "y la del frente": a window clause going on without saying "ventana". */
    private static final Pattern SIGUE = w("otra|otras|una|un|dos|2|la de|la del|las de|las del|another|one|two|the one|the ones");
    private static final Pattern DOS = w("dos|2|un par de|par de|two|a couple of|couple of|ambas|both");
    private static final Pattern MAS_DE_DOS = w("tres|3|cuatro|4|cinco|5|seis|6|three|four|five|six|varias|muchas|several|many|lots of");
    private static final Pattern FRENTE = w("frente|delante|adelante|delantera|delantero|frontal|fachada|entrada|puerta|front|facade");
    private static final Pattern ATRAS = w("atras|detras|trasera|trasero|fondo|espalda|back|rear|behind");
    private static final Pattern IZQUIERDA = w("izquierda|izquierdo|izq|left");
    private static final Pattern DERECHA = w("derecha|derecho|der|right");
    private static final Pattern Y = w("y|e|and|pero|but|ademas|tambien|also|luego|then|plus|mejor|sino");
    private static final Pattern NIEGA = w("no|nunca|ni|sin|not|dont|don t|never|without");

    private static Pattern w(String alternatives) {
        return Pattern.compile("(?<![a-z0-9])(" + alternatives + ")(?![a-z0-9])");
    }

    /** Lower case, no accents, "ñ" as "n"; only letters, digits and single spaces. */
    public static String llano(@Nullable String text) {
        if (text == null) {
            return "";
        }
        String n = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.replaceAll("[^a-z0-9]+", " ").trim();
    }

    /**
     * What these words ask of a house: "mejor una cabaña de abeto", "techo alto y dos ventanas a la izquierda". Read
     * clause by clause, so "no quiero una cabaña, mejor la casa larga" is the long house and "no abras ventanas" opens none.
     */
    public static Pedido leer(@Nullable String text) {
        ModelosCasa.Modelo modelo = null;
        Plano.Madera madera = null;
        ModelosCasa.Cubierta cubierta = null;
        List<Ventanas> ventanas = new ArrayList<>();
        Ventanas antes = null;
        for (String parte : (text == null ? "" : text.toLowerCase(Locale.ROOT)).split("[,;.!?]+")) {
            for (String c : Y.split(llano(parte))) {
                c = c.trim();
                if (c.isEmpty()) {
                    continue;
                }
                boolean niega = NIEGA.matcher(c).find();
                if (!niega) {
                    ModelosCasa.Modelo m = CABANA.matcher(c).find() ? ModelosCasa.Modelo.CABANA : LARGA.matcher(c).find() ? ModelosCasa.Modelo.LARGA
                            : DOS_PISOS.matcher(c).find() ? ModelosCasa.Modelo.DOS_PISOS : null;
                    modelo = m != null ? m : modelo;
                    Plano.Madera md = Plano.Madera.palabra(c);
                    madera = md != null ? md : madera;
                    ModelosCasa.Cubierta cu = ALTO.matcher(c).find() ? ModelosCasa.Cubierta.ALTO : TRAVES.matcher(c).find() ? ModelosCasa.Cubierta.TRAVES
                            : DOS_AGUAS.matcher(c).find() ? ModelosCasa.Cubierta.DOS_AGUAS : null;
                    cubierta = cu != null ? cu : cubierta;
                }
                Lado lado = lado(c);
                boolean ventana = VENTANA.matcher(c).find();
                if (!ventana && (antes == null || lado == null || !SIGUE.matcher(c).find())) {
                    continue;
                }
                boolean cierra = CERRAR.matcher(c).find();
                if (niega && !cierra) {
                    continue; // "no abras ventanas", "no cierres esa": nothing to do
                }
                boolean abrir = ventana ? !cierra : antes.abrir();
                int cuantas = MAS_DE_DOS.matcher(c).find() ? 3 : DOS.matcher(c).find() ? 2 : PLURAL.matcher(c).find() && !UNA.matcher(c).find() ? 2 : 1;
                antes = new Ventanas(abrir, cuantas, lado);
                ventanas.add(antes);
            }
        }
        return new Pedido(modelo, madera, cubierta, ventanas);
    }

    @Nullable
    private static Lado lado(String c) {
        if (IZQUIERDA.matcher(c).find()) {
            return Lado.IZQUIERDA;
        }
        if (DERECHA.matcher(c).find()) {
            return Lado.DERECHA;
        }
        if (ATRAS.matcher(c).find()) {
            return Lado.ATRAS;
        }
        return FRENTE.matcher(c).find() ? Lado.FRENTE : null;
    }

    // ------------------------------------------------------------------ doing it

    /**
     * Does all that was asked to the draft, or none of it: a new design starts from the catalogue's (in the wood
     * asked, or the draft's), then the wood, the roof and the windows, each checked by the catalogue as a whole house.
     */
    public static Hecho aplicar(CatalogoPlanos.Borrador b, Pedido p) {
        CatalogoPlanos.Borrador antes = b;
        List<String> es = new ArrayList<>();
        List<String> en = new ArrayList<>();
        if (p.modelo() != null && p.modelo() != b.modelo()) {
            b = CatalogoPlanos.Borrador.de(p.modelo(), p.madera() != null ? p.madera() : b.madera());
            es.add("ahora es una " + p.modelo().nombre(true));
            en.add("now it's a " + p.modelo().nombre(false));
        }
        if (p.madera() != null && p.madera() != b.madera()) {
            b = b.en(p.madera());
        }
        if (p.madera() != null && p.madera() != antes.madera()) {
            es.add("de " + p.madera().es);
            en.add("in " + p.madera().en);
        }
        if (p.cubierta() != null && p.cubierta() != b.cubierta()) {
            CatalogoPlanos.Resultado r = CatalogoPlanos.cubierta(b, p.cubierta());
            if (!r.ok()) {
                return no(antes, r.porQue(), r.porQueEn());
            }
            b = r.borrador();
            es.add("con " + p.cubierta().es);
            en.add("with a " + p.cubierta().en);
        }
        if (!p.ventanas().isEmpty()) {
            List<BlockPos> abrir = new ArrayList<>();
            List<BlockPos> cerrar = new ArrayList<>();
            int abrirPedidas = p.ventanas().stream().filter(Ventanas::abrir).mapToInt(Ventanas::cuantas).sum();
            int cerrarPedidas = p.ventanas().stream().filter(v -> !v.abrir()).mapToInt(Ventanas::cuantas).sum();
            if (abrirPedidas > CatalogoPlanos.VENTANAS_POR_CAMBIO || cerrarPedidas > CatalogoPlanos.VENTANAS_POR_CAMBIO) {
                return no(antes, "cambio de a " + CatalogoPlanos.VENTANAS_POR_CAMBIO + " ventanas por vez",
                        "I change " + CatalogoPlanos.VENTANAS_POR_CAMBIO + " windows at a time");
            }
            for (Ventanas v : p.ventanas()) {
                List<BlockPos> elegidas = v.abrir() ? paraAbrir(b, v.cuantas(), v.lado(), abrir, cerrar) : paraCerrar(b, v.cuantas(), v.lado(), abrir, cerrar);
                String dondeEs = v.lado() == null ? "en ninguna pared" : v.lado().es;
                String dondeEn = v.lado() == null ? "on any wall" : v.lado().en;
                if (elegidas.isEmpty()) {
                    return v.abrir() ? no(antes, "no me cabe otra ventana " + dondeEs, "no other window fits " + dondeEn)
                            : no(antes, "no hay ventana que cerrar " + dondeEs, "there's no window to wall up " + dondeEn);
                }
                (v.abrir() ? abrir : cerrar).addAll(elegidas);
                Lado donde = lado(b.modelo(), elegidas.get(0));
                String lugarEs = donde == null ? "" : " " + donde.es;
                String lugarEn = donde == null ? "" : " " + donde.en;
                int n = elegidas.size();
                String solo = n < v.cuantas() ? "solo cupo " : "";
                String only = n < v.cuantas() ? "only " : "";
                es.add(solo + (n == 1 ? "una ventana " : "dos ventanas ") + (v.abrir() ? "más" : "menos") + lugarEs);
                en.add(only + (n == 1 ? "one " : "two ") + (v.abrir() ? "more" : "fewer") + (n == 1 ? " window" : " windows") + lugarEn);
            }
            CatalogoPlanos.Resultado r = CatalogoPlanos.ventanas(b, abrir, cerrar);
            if (!r.ok()) {
                return no(antes, r.porQue(), r.porQueEn());
            }
            b = r.borrador();
        }
        if (es.isEmpty()) {
            return no(antes, "así ya está", "it's like that already");
        }
        return new Hecho(antes, b, CatalogoPlanos.diferencia(antes.plano(), b.plano()), String.join(", ", es), String.join(", ", en));
    }

    private static Hecho no(CatalogoPlanos.Borrador antes, String es, String en) {
        return new Hecho(antes, null, null, es, en);
    }

    /** The wall a cell of this design is in, as seen from in front of the door; null off the walls. */
    @Nullable
    public static Lado lado(ModelosCasa.Modelo m, BlockPos rel) {
        if (rel.getZ() == m.z0) {
            return Lado.FRENTE;
        }
        if (rel.getZ() == m.z1) {
            return Lado.ATRAS;
        }
        if (rel.getX() == m.x1) {
            return Lado.IZQUIERDA;
        }
        return rel.getX() == m.x0 ? Lado.DERECHA : null;
    }

    /**
     * Where to open {@code n} windows: on the wall asked (or the one with fewest), at the height of the house's own
     * windows, away from the others and near the middle of the wall; each one the catalogue takes with the rest.
     */
    private static List<BlockPos> paraAbrir(CatalogoPlanos.Borrador b, int n, @Nullable Lado lado, List<BlockPos> yaAbrir, List<BlockPos> yaCerrar) {
        Plano base = b.plano();
        ModelosCasa.Modelo m = b.modelo();
        List<BlockPos> vidrios = new ArrayList<>();
        Map<Integer, Integer> alturas = new HashMap<>();
        Map<Lado, Integer> porLado = new HashMap<>();
        for (Plano.Pieza p : base.piezas) {
            if (p.rol() == Plano.Rol.VIDRIO) {
                vidrios.add(p.rel());
                alturas.merge(p.rel().getY(), 1, Integer::sum);
                Lado l = lado(m, p.rel());
                if (l != null) {
                    porLado.merge(l, 1, Integer::sum);
                }
            }
        }
        int alto = 2;
        int vistas = 0;
        for (Map.Entry<Integer, Integer> e : alturas.entrySet()) {
            if (e.getValue() > vistas || e.getValue() == vistas && e.getKey() < alto) {
                alto = e.getKey();
                vistas = e.getValue();
            }
        }
        final int altura = alto;
        List<Lado> lados = new ArrayList<>(lado != null ? List.of(lado) : List.of(Lado.FRENTE, Lado.IZQUIERDA, Lado.DERECHA, Lado.ATRAS));
        lados.sort(Comparator.comparingInt(l -> porLado.getOrDefault(l, 0)));
        List<BlockPos> out = new ArrayList<>();
        for (Lado l : lados) {
            List<BlockPos> cand = new ArrayList<>();
            for (Plano.Pieza p : base.piezas) {
                if (lado(m, p.rel()) == l && !yaAbrir.contains(p.rel()) && !yaCerrar.contains(p.rel()) && ModelosCasa.pared(m, base, p.rel())) {
                    cand.add(p.rel());
                }
            }
            while (out.size() < n && !cand.isEmpty()) {
                List<BlockPos> cerca = new ArrayList<>(vidrios);
                cerca.addAll(yaAbrir);
                cerca.addAll(out);
                cand.sort(Comparator.comparingInt((BlockPos c) -> Math.abs(c.getY() - altura))
                        .thenComparingInt(c -> -lejos(m, c, cerca))
                        .thenComparingInt(c -> medio(m, c))
                        .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX));
                BlockPos elegida = null;
                for (BlockPos c : cand) {
                    List<BlockPos> prueba = new ArrayList<>(yaAbrir);
                    prueba.addAll(out);
                    prueba.add(c);
                    if (CatalogoPlanos.ventanas(b, prueba, yaCerrar).ok()) {
                        elegida = c;
                        break;
                    }
                }
                if (elegida == null) {
                    break;
                }
                out.add(elegida);
                cand.remove(elegida);
            }
            if (!out.isEmpty()) {
                break; // one wall per request
            }
        }
        return out;
    }

    /** Which windows to wall up: on the wall asked (or the one with most), those opened in this draft first. */
    private static List<BlockPos> paraCerrar(CatalogoPlanos.Borrador b, int n, @Nullable Lado lado, List<BlockPos> yaAbrir, List<BlockPos> yaCerrar) {
        Plano base = b.plano();
        ModelosCasa.Modelo m = b.modelo();
        Map<Lado, List<BlockPos>> porLado = new HashMap<>();
        for (Plano.Pieza p : base.piezas) {
            Lado l = lado(m, p.rel());
            if (p.rol() == Plano.Rol.VIDRIO && l != null && !yaAbrir.contains(p.rel()) && !yaCerrar.contains(p.rel())) {
                porLado.computeIfAbsent(l, k -> new ArrayList<>()).add(p.rel());
            }
        }
        List<Lado> lados = new ArrayList<>(lado != null ? List.of(lado) : List.of(Lado.FRENTE, Lado.IZQUIERDA, Lado.DERECHA, Lado.ATRAS));
        lados.sort(Comparator.comparingInt(l -> -porLado.getOrDefault(l, List.of()).size()));
        List<BlockPos> out = new ArrayList<>();
        for (Lado l : lados) {
            List<BlockPos> cand = new ArrayList<>(porLado.getOrDefault(l, List.of()));
            cand.sort(Comparator.comparingInt((BlockPos c) -> b.abiertas().contains(c) ? 0 : 1)
                    .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX));
            for (BlockPos c : cand) {
                if (out.size() >= n) {
                    break;
                }
                List<BlockPos> prueba = new ArrayList<>(yaCerrar);
                prueba.addAll(out);
                prueba.add(c);
                if (CatalogoPlanos.ventanas(b, yaAbrir, prueba).ok()) {
                    out.add(c);
                }
            }
            if (!out.isEmpty()) {
                break;
            }
        }
        return out;
    }

    /** How far the cell is from the nearest window on its own wall (99 with none there). */
    private static int lejos(ModelosCasa.Modelo m, BlockPos c, List<BlockPos> otras) {
        Lado l = lado(m, c);
        int d = 99;
        for (BlockPos o : otras) {
            if (lado(m, o) == l) {
                d = Math.min(d, Math.abs(c.getX() - o.getX()) + Math.abs(c.getZ() - o.getZ()) + Math.abs(c.getY() - o.getY()));
            }
        }
        return d;
    }

    /** How far from the middle of its wall, in half blocks. */
    private static int medio(ModelosCasa.Modelo m, BlockPos c) {
        Lado l = lado(m, c);
        return l == Lado.FRENTE || l == Lado.ATRAS ? Math.abs(2 * c.getX() - (m.x0 + m.x1)) : Math.abs(2 * c.getZ() - (m.z0 + m.z1));
    }

    // ------------------------------------------------------------------ a plain yes, a plain no

    private static final Set<String> SI = Set.of("si", "sii", "siii", "sip", "dale", "va", "vale", "ok", "okay", "okey", "oki", "perfecto", "perfecta",
            "genial", "listo", "adelante", "hazla", "hazlo", "hazmela", "construyela", "construyemela", "levantala", "armala", "empieza", "empiezala",
            "comienza", "comenzala", "apruebo", "aprobada", "aprobado", "acuerdo", "gusta", "encanta", "venga", "hagamoslo", "exacto", "claro", "obvio",
            "bueno", "bien", "seguro", "yes", "yeah", "yep", "yup", "sure", "build", "approve", "approved", "perfect", "great", "good", "fine", "nice",
            "ahead");
    /** Said together, a yes: "de acuerdo", "do it". */
    private static final Map<String, String> SI_FRASES = Map.of("de acuerdo", "dale", "esta bien", "dale", "me gusta", "dale", "go ahead", "dale",
            "do it", "dale", "lets do it", "dale", "build it", "dale");
    private static final Set<String> SI_RELLENO = Set.of("por", "favor", "porfa", "porfavor", "entonces", "pues", "ya", "asi", "esta", "esa", "eso", "mismo",
            "me", "la", "lo", "una", "un", "casa", "cabana", "de", "muy", "mucho", "todo", "ahora", "nomas", "te", "quedo", "hazme", "go", "ahead", "do",
            "it", "that", "one", "looks", "sounds", "i", "like", "love", "lets", "let", "s", "please", "then", "so", "the", "house", "a", "start", "is",
            "this", "just", "right", "now", "and", "y", "que", "sea", "como", "dijiste", "hagala", "construye");
    private static final Set<String> NO = Set.of("no", "nop", "nope", "nah", "nel", "cancela", "cancelala", "cancelalo", "dejalo", "dejala", "olvidalo",
            "olvidala", "olvidate", "nunca", "cancel", "forget", "pass", "paso");
    private static final Set<String> NO_RELLENO = Set.of("gracias", "thanks", "thank", "you", "mejor", "asi", "la", "lo", "casa", "esa", "eso", "por", "ahora",
            "todavia", "aun", "it", "that", "one", "the", "house", "quiero", "want", "dont", "don", "t", "i", "de", "about", "para", "nada", "ya", "pues",
            "entonces", "then", "mas", "tarde", "later", "maybe", "not", "now", "otra", "vez", "porfa", "favor");

    /** "Sí", "dale, hazla", "ok build it": a yes and nothing else (no question, no "pero", no change asked). */
    public static boolean si(@Nullable String text, @Nullable String nombre) {
        return respuesta(text, nombre, SI, SI_RELLENO, NO);
    }

    /** "No", "no, gracias", "mejor no", "olvídalo": a no and nothing else. */
    public static boolean no(@Nullable String text, @Nullable String nombre) {
        return respuesta(text, nombre, NO, NO_RELLENO, SI);
    }

    private static boolean respuesta(@Nullable String text, @Nullable String nombre, Set<String> fuerte, Set<String> relleno, Set<String> contra) {
        if (text == null || text.indexOf('?') >= 0 || text.indexOf('¿') >= 0) {
            return false;
        }
        String t = " " + llano(text) + " ";
        for (String parte : llano(nombre).split(" ")) {
            if (parte.length() >= 2) {
                t = t.replace(" " + parte + " ", " ");
            }
        }
        if (fuerte == SI) {
            for (Map.Entry<String, String> e : SI_FRASES.entrySet()) {
                t = t.replace(" " + e.getKey() + " ", " " + e.getValue() + " ");
            }
        }
        t = t.trim();
        if (t.isEmpty()) {
            return false;
        }
        String[] words = t.split(" ");
        if (words.length > 8) {
            return false;
        }
        boolean strong = false;
        for (String word : words) {
            if (contra.contains(word) && !relleno.contains(word)) {
                return false;
            }
            if (fuerte.contains(word)) {
                strong = true;
            } else if (!relleno.contains(word)) {
                return false;
            }
        }
        return strong;
    }

    // ------------------------------------------------------------------ in words

    /** "165 piezas: 60 tablas, 34 escalones, …": every piece of the plan, counted exactly. */
    public static String piezas(Plano plano, boolean es) {
        StringBuilder s = new StringBuilder();
        int total = 0;
        for (Map.Entry<Plano.Rol, Integer> e : plano.materiales.entrySet()) {
            if (!e.getKey().bloque() || e.getValue() <= 0) {
                continue;
            }
            total += e.getValue();
            s.append(s.isEmpty() ? "" : ", ").append(e.getValue()).append(' ').append(nombre(e.getKey(), e.getValue(), es));
        }
        return total + (es ? " piezas exactas: " : " exact pieces: ") + s;
    }

    /** "2 paneles de vidrio más y 2 tablas menos": the real difference, in pieces. */
    public static String diferencia(CatalogoPlanos.Diferencia d, boolean es) {
        List<String> mas = new ArrayList<>();
        List<String> menos = new ArrayList<>();
        for (Map.Entry<Plano.Rol, Integer> e : d.materiales().entrySet()) {
            if (!e.getKey().bloque()) {
                continue;
            }
            int n = e.getValue();
            String x = Math.abs(n) + " " + nombre(e.getKey(), n, es);
            (n > 0 ? mas : menos).add(x);
        }
        if (mas.isEmpty() && menos.isEmpty()) {
            return es ? "las mismas piezas, " + d.cambiadas() + " cambian de sitio o de forma" : "the same pieces, " + d.cambiadas() + " change place or shape";
        }
        String a = mas.isEmpty() ? "" : lista(mas, es) + (es ? " más" : " more");
        String b = menos.isEmpty() ? "" : lista(menos, es) + (es ? " menos" : " fewer");
        return a + (a.isEmpty() || b.isEmpty() ? "" : "; ") + b;
    }

    private static String lista(List<String> l, boolean es) {
        return l.size() == 1 ? l.get(0) : String.join(", ", l.subList(0, l.size() - 1)) + (es ? " y " : " and ") + l.get(l.size() - 1);
    }

    static String nombre(Plano.Rol r, int n, boolean es) {
        boolean uno = Math.abs(n) == 1;
        return switch (r) {
            case BASE -> es ? (uno ? "bloque de piedra" : "bloques de piedra") : (uno ? "stone block" : "stone blocks");
            case TRONCO -> es ? (uno ? "tronco" : "troncos") : (uno ? "log" : "logs");
            case TABLA -> es ? (uno ? "tabla" : "tablas") : (uno ? "plank" : "planks");
            case ESCALON -> es ? (uno ? "escalón" : "escalones") : (uno ? "stair" : "stairs");
            case PUERTA -> es ? (uno ? "puerta" : "puertas") : (uno ? "door" : "doors");
            case VIDRIO -> es ? (uno ? "panel de vidrio" : "paneles de vidrio") : (uno ? "glass pane" : "glass panes");
            case ANTORCHA -> es ? (uno ? "antorcha" : "antorchas") : (uno ? "torch" : "torches");
            case MESA -> es ? (uno ? "mesa de crafteo" : "mesas de crafteo") : (uno ? "crafting table" : "crafting tables");
            case COFRE -> es ? (uno ? "cofre" : "cofres") : (uno ? "chest" : "chests");
            default -> es ? r.es : r.en;
        };
    }
}
