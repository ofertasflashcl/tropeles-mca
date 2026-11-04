package net.mca.entity.ai.obra;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * The houses a villager can offer, always in the same order, each by its id: the first house and the designs of
 * {@link ModelosCasa}. Looked up by id or by what fits (a plot's width and depth either way round, its height, the
 * floors, the wood), each with its names and its measures. A choice becomes a {@link Borrador}: a draft that can still
 * be changed in small, checked ways (up to two windows opened and two walled up at a time, another roof its walls can
 * carry), every change a new revision with the real difference in pieces from the one before. Only drafts change here:
 * a plan set down as a work is never touched (it keeps its own copy), and nothing here reaches the world or the network.
 */
public final class CatalogoPlanos {
    /** At most this many windows opened, and this many walled up, in one change. */
    public static final int VENTANAS_POR_CAMBIO = 2;
    /** At most this many windows of a draft differ from its design. */
    public static final int VENTANAS_MAX = 8;

    /** A house in the catalogue: what it is called and how it measures (in oak; every wood measures the same). */
    public record Ficha(ModelosCasa.Modelo modelo, String id, int revision, String nombre, String nombreEn, String descripcion, String descripcionEn,
                        int pisos, int ancho, int fondo, int alto, int bloques, Map<Plano.Rol, Integer> materiales, List<ModelosCasa.Cubierta> cubiertas,
                        List<Plano.Madera> maderas) {
        public String nombre(boolean es) {
            return es ? nombre : nombreEn;
        }

        public String descripcion(boolean es) {
            return es ? descripcion : descripcionEn;
        }
    }

    /** What a plot allows: its width and depth (either way round), its height, how many floors (0: any), the wood (null: any). */
    public record Filtro(int ancho, int fondo, int alto, int pisos, @Nullable Plano.Madera madera) {
        public static Filtro parcela(int ancho, int fondo) {
            return new Filtro(ancho, fondo, ModelosCasa.ALTO_MAX, 0, null);
        }

        public boolean cabe(Ficha f) {
            boolean planta = f.ancho() <= ancho && f.fondo() <= fondo || f.fondo() <= ancho && f.ancho() <= fondo;
            return planta && f.alto() <= alto && (pisos <= 0 || f.pisos() == pisos) && (madera == null || f.maderas().contains(madera));
        }
    }

    private static List<Ficha> fichas;

    private CatalogoPlanos() {
    }

    /** Every house, in id order. */
    public static synchronized List<Ficha> fichas() {
        if (fichas == null) {
            List<Ficha> list = new ArrayList<>();
            for (ModelosCasa.Modelo m : ModelosCasa.Modelo.values()) {
                list.add(ficha(m));
            }
            list.sort(Comparator.comparing(Ficha::id));
            fichas = List.copyOf(list);
        }
        return fichas;
    }

    @Nullable
    public static Ficha ficha(String id) {
        for (Ficha f : fichas()) {
            if (f.id().equals(id)) {
                return f;
            }
        }
        return null;
    }

    /** The houses that fit, in id order. */
    public static List<Ficha> buscar(Filtro filtro) {
        List<Ficha> out = new ArrayList<>();
        for (Ficha f : fichas()) {
            if (filtro.cabe(f)) {
                out.add(f);
            }
        }
        return out;
    }

    /** The catalogue's own plan of a house in a wood, or null for an id it does not have. */
    @Nullable
    public static Plano plano(String id, Plano.Madera madera) {
        ModelosCasa.Modelo m = ModelosCasa.Modelo.de(id);
        return m == null ? null : Borrador.de(m, madera).plano();
    }

    private static Ficha ficha(ModelosCasa.Modelo m) {
        Plano p = Borrador.de(m, Plano.Madera.ROBLE).plano();
        ModelosCasa.Revision r = ModelosCasa.revisar(p, Direction.NORTH);
        List<ModelosCasa.Cubierta> cubiertas = new ArrayList<>();
        for (ModelosCasa.Cubierta c : ModelosCasa.Cubierta.values()) {
            Plano q = ModelosCasa.generar(m, Plano.Madera.ROBLE, c, List.of(), List.of(), m.revision(), new String[2]);
            if (q != null && ModelosCasa.revisar(q, Direction.NORTH).ok()) {
                cubiertas.add(c);
            }
        }
        return new Ficha(m, m.id(), m.revision(), m.nombre(true), m.nombre(false), m.descripcion, m.descripcionEn, r.pisos(), r.ancho(), r.fondo(),
                r.alto(), r.bloques(), p.materiales, List.copyOf(cubiertas), List.of(Plano.Madera.values()));
    }

    // ------------------------------------------------------------------ drafts

    private static final Map<Borrador, Plano> PLANOS = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Borrador, Plano> eldest) {
            return size() > 64;
        }
    };

    /**
     * A house being chosen: which design, in which wood, under which roof, which windows opened and walled up (plan
     * coordinates), and its revision. Its plan is drawn from these, the same every time.
     */
    public record Borrador(ModelosCasa.Modelo modelo, Plano.Madera madera, ModelosCasa.Cubierta cubierta, List<BlockPos> abiertas, List<BlockPos> cerradas,
                           int revision) {
        public Borrador {
            Objects.requireNonNull(modelo);
            Objects.requireNonNull(madera);
            Objects.requireNonNull(cubierta);
            abiertas = List.copyOf(abiertas);
            cerradas = List.copyOf(cerradas);
        }

        /** A design as the catalogue has it, in this wood. */
        public static Borrador de(ModelosCasa.Modelo modelo, Plano.Madera madera) {
            return new Borrador(modelo, madera, ModelosCasa.Cubierta.DOS_AGUAS, List.of(), List.of(), modelo.revision());
        }

        /** The same draft in another wood (the wood is what it is built of: same design, same revision, other blocks). */
        public Borrador en(Plano.Madera otra) {
            return new Borrador(modelo, otra, cubierta, abiertas, cerradas, revision);
        }

        /** Its plan: the same object every time for the same draft. */
        public Plano plano() {
            synchronized (PLANOS) {
                Plano p = PLANOS.get(this);
                if (p == null) {
                    String[] why = {"", ""};
                    p = ModelosCasa.generar(modelo, madera, cubierta, abiertas, cerradas, revision, why);
                    if (p == null) {
                        throw new IllegalStateException("no plan for " + this + ": " + why[1]);
                    }
                    PLANOS.put(this, p);
                }
                return p;
            }
        }
    }

    /** A draft of a house from the catalogue, or null for an id it does not have. */
    @Nullable
    public static Borrador borrador(String id, Plano.Madera madera) {
        ModelosCasa.Modelo m = ModelosCasa.Modelo.de(id);
        return m == null ? null : Borrador.de(m, madera);
    }

    /** One cell that differs between two plans: what was there (null: nothing) and what is there now (null: nothing). */
    public record Cambio(BlockPos rel, @Nullable Plano.Pieza antes, @Nullable Plano.Pieza despues) {
    }

    /** The real difference between two plans, cell by cell, and in pieces of each kind (after minus before). */
    public record Diferencia(List<Cambio> cambios, Map<Plano.Rol, Integer> materiales) {
        public int agregadas() {
            return (int) cambios.stream().filter(c -> c.antes() == null).count();
        }

        public int quitadas() {
            return (int) cambios.stream().filter(c -> c.despues() == null).count();
        }

        public int cambiadas() {
            return (int) cambios.stream().filter(c -> c.antes() != null && c.despues() != null).count();
        }

        public boolean vacia() {
            return cambios.isEmpty();
        }

        /** "+2 vidrio, -2 tronco; 2 celdas cambian, 0 se agregan, 0 se quitan". */
        public String resumen(boolean es) {
            StringBuilder s = new StringBuilder();
            for (Map.Entry<Plano.Rol, Integer> e : materiales.entrySet()) {
                if (!s.isEmpty()) {
                    s.append(", ");
                }
                s.append(e.getValue() > 0 ? "+" : "").append(e.getValue()).append(' ').append(es ? e.getKey().es : e.getKey().en);
            }
            if (s.isEmpty()) {
                s.append(es ? "los mismos materiales" : "the same materials");
            }
            return s + (es ? "; " + cambiadas() + " celdas cambian, " + agregadas() + " se agregan, " + quitadas() + " se quitan"
                    : "; " + cambiadas() + " cells change, " + agregadas() + " are added, " + quitadas() + " are removed");
        }
    }

    public static Diferencia diferencia(Plano antes, Plano despues) {
        Map<BlockPos, Plano.Pieza> a = porCelda(antes);
        Map<BlockPos, Plano.Pieza> b = porCelda(despues);
        Set<BlockPos> todas = new TreeSet<>(Comparator.comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX));
        todas.addAll(a.keySet());
        todas.addAll(b.keySet());
        List<Cambio> cambios = new ArrayList<>();
        for (BlockPos p : todas) {
            Plano.Pieza x = a.get(p);
            Plano.Pieza y = b.get(p);
            if (x == null || y == null || x.rol() != y.rol() || !x.state().equals(y.state())) {
                cambios.add(new Cambio(p, x, y));
            }
        }
        Map<Plano.Rol, Integer> m = new EnumMap<>(Plano.Rol.class);
        for (Plano.Rol r : Plano.Rol.values()) {
            int d = despues.materiales.getOrDefault(r, 0) - antes.materiales.getOrDefault(r, 0);
            if (d != 0) {
                m.put(r, d);
            }
        }
        return new Diferencia(List.copyOf(cambios), Collections.unmodifiableMap(m));
    }

    private static Map<BlockPos, Plano.Pieza> porCelda(Plano p) {
        Map<BlockPos, Plano.Pieza> m = new HashMap<>();
        for (Plano.Pieza x : p.piezas) {
            m.put(x.rel(), x);
        }
        return m;
    }

    /**
     * What a change to a draft came to: the new draft and its plan, the plan it was made from and the difference
     * between them; or no draft, and why not (Spanish, English).
     */
    public record Resultado(@Nullable Borrador borrador, Plano base, @Nullable Plano plano, @Nullable Diferencia diferencia, String porQue,
                            String porQueEn) {
        public boolean ok() {
            return borrador != null;
        }

        static Resultado no(Plano base, String es, String en) {
            return new Resultado(null, base, null, null, es, en);
        }
    }

    /** Where a window could be opened in this draft's walls (plan coordinates, bottom up), each one checked alone. */
    public static List<BlockPos> ventanasPosibles(Borrador b) {
        Plano base = b.plano();
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> vistas = new TreeSet<>(Comparator.comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX));
        for (Plano.Pieza p : base.piezas) {
            if (ModelosCasa.pared(b.modelo(), base, p.rel())) {
                vistas.add(p.rel());
            }
        }
        for (BlockPos p : vistas) {
            if (ventanas(b, List.of(p), List.of()).ok()) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * Opens windows at {@code abrir} and walls up those at {@code cerrar} (plan coordinates; at most
     * {@value #VENTANAS_POR_CAMBIO} of each): a wall cell between the floor and the roof becomes a pane, a pane becomes
     * the wall it is in. The result is checked as a whole house; a change that would leave a torch hanging, a door
     * blocked or a room shut is refused, with why.
     */
    public static Resultado ventanas(Borrador b, List<BlockPos> abrir, List<BlockPos> cerrar) {
        Plano base = b.plano();
        if (abrir.isEmpty() && cerrar.isEmpty()) {
            return Resultado.no(base, "no me dijiste qué ventanas cambiar", "you didn't say which windows to change");
        }
        if (abrir.size() > VENTANAS_POR_CAMBIO || cerrar.size() > VENTANAS_POR_CAMBIO) {
            return Resultado.no(base, "cambio de a " + VENTANAS_POR_CAMBIO + " ventanas por vez", "I change " + VENTANAS_POR_CAMBIO + " windows at a time");
        }
        Map<BlockPos, Plano.Pieza> celdas = porCelda(base);
        Set<BlockPos> originales = ModelosCasa.ventanasDe(b.modelo());
        Set<BlockPos> abiertas = new LinkedHashSet<>(b.abiertas());
        Set<BlockPos> cerradas = new LinkedHashSet<>(b.cerradas());
        for (BlockPos p : abrir) {
            Plano.Pieza q = celdas.get(p);
            if (cerrar.contains(p)) {
                return Resultado.no(base, "no puedo abrir y cerrar la misma ventana (" + ModelosCasa.texto(p) + ")",
                        "I can't open and wall up the same window (" + ModelosCasa.texto(p) + ")");
            }
            if (q != null && q.rol() == Plano.Rol.VIDRIO) {
                return Resultado.no(base, "en " + ModelosCasa.texto(p) + " ya hay una ventana", "there is a window at " + ModelosCasa.texto(p) + " already");
            }
            if (!ModelosCasa.pared(b.modelo(), base, p)) {
                return Resultado.no(base, "en " + ModelosCasa.texto(p) + " no hay pared con un cuarto detrás donde abrir una ventana",
                        "at " + ModelosCasa.texto(p) + " there is no wall with a room behind it to open a window in");
            }
            if (!cerradas.remove(p)) {
                abiertas.add(p);
            }
        }
        for (BlockPos p : cerrar) {
            Plano.Pieza q = celdas.get(p);
            if (q == null || q.rol() != Plano.Rol.VIDRIO) {
                return Resultado.no(base, "en " + ModelosCasa.texto(p) + " no hay ventana que cerrar", "there is no window at " + ModelosCasa.texto(p) + " to wall up");
            }
            if (!abiertas.remove(p)) {
                if (!originales.contains(p)) {
                    return Resultado.no(base, "la ventana de " + ModelosCasa.texto(p) + " no es de la casa", "the window at " + ModelosCasa.texto(p) + " isn't the house's");
                }
                cerradas.add(p);
            }
        }
        if (abiertas.size() + cerradas.size() > VENTANAS_MAX) {
            return Resultado.no(base, "ya cambié " + VENTANAS_MAX + " ventanas de esta casa: más ya es otra casa",
                    "I've changed " + VENTANAS_MAX + " windows of this house already: more would be another house");
        }
        return revisado(b, new Borrador(b.modelo(), b.madera(), b.cubierta(), ordenadas(abiertas), ordenadas(cerradas), b.revision() + 1));
    }

    /** Puts another roof on the draft: one its walls can carry, of those its design takes. */
    public static Resultado cubierta(Borrador b, ModelosCasa.Cubierta c) {
        Plano base = b.plano();
        if (c == b.cubierta()) {
            return Resultado.no(base, "ya tiene " + c.es, "it has a " + c.en + " already");
        }
        String[] why = {"", ""};
        if (!ModelosCasa.admite(b.modelo(), c, why)) {
            return Resultado.no(base, why[0], why[1]);
        }
        return revisado(b, new Borrador(b.modelo(), b.madera(), c, b.abiertas(), b.cerradas(), b.revision() + 1));
    }

    private static Resultado revisado(Borrador b, Borrador nuevo) {
        Plano base = b.plano();
        String[] why = {"", ""};
        Plano p = ModelosCasa.generar(nuevo.modelo(), nuevo.madera(), nuevo.cubierta(), nuevo.abiertas(), nuevo.cerradas(), nuevo.revision(), why);
        if (p == null) {
            return Resultado.no(base, why[0], why[1]);
        }
        ModelosCasa.Revision r = ModelosCasa.revisar(p, Direction.NORTH);
        if (!r.ok()) {
            return Resultado.no(base, "así la casa no sirve: " + r.problemas().get(0).es(), "that way the house won't do: " + r.problemas().get(0).en());
        }
        synchronized (PLANOS) {
            PLANOS.put(nuevo, p);
        }
        return new Resultado(nuevo, base, p, diferencia(base, p), "", "");
    }

    private static List<BlockPos> ordenadas(Set<BlockPos> s) {
        List<BlockPos> l = new ArrayList<>(s);
        l.sort(Comparator.comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX));
        return l;
    }
}
