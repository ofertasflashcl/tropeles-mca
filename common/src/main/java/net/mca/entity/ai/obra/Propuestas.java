package net.mca.entity.ai.obra;

import net.mca.MCA;
import net.mca.util.WorldUtils;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The houses proposed and not yet approved in one world (C03b), one per builder, kept with the world: the exact plan
 * offered (its id, revision and hash, and the draft it came from), where and which way round, by whom and for whom.
 * Nothing is built from a proposal and nothing of it is in {@link ObrasMundo}: only the player it is for, saying yes
 * themselves, turns it into an {@link Obra} with exactly this plan ({@link net.mca.entity.ai.mente.Voluntad#respuestaPropuesta}).
 * A language model can propose again, change it or drop it, never approve it.
 */
public final class Propuestas extends PersistentState {
    /** The ghost of a proposal goes by this, then its id: never mistaken for a building's. */
    public static final String PREFIJO = "propuesta:";
    public static final String PARCELA = "parcela:";
    /** A day of play with no word about it: it is dropped. */
    public static final long CADUCA = 24000L;
    /** For this long after it was shown (or changed) a bare "sí" or "no" answers it. */
    public static final long RESPUESTA = 6000L;

    private final Map<UUID, Propuesta> porConstructor = new LinkedHashMap<>();

    public static Propuestas get(ServerWorld world) {
        return WorldUtils.loadData(world, Propuestas::leer, w -> new Propuestas(), "mca_tropeles_propuestas");
    }

    Propuestas() {
    }

    public static Propuestas leer(NbtCompound nbt) {
        Propuestas ps = new Propuestas();
        NbtList list = nbt.getList("propuestas", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            Propuesta p = Propuesta.leer(list.getCompound(i));
            if (p != null && !ps.porConstructor.containsKey(p.constructor)) {
                ps.porConstructor.put(p.constructor, p);
            } else {
                ps.markDirty(); // nothing stands of a proposal: the builder offers it again when asked
            }
        }
        return ps;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (Propuesta p : porConstructor.values()) {
            list.add(p.guardar());
        }
        nbt.put("propuestas", list);
        return nbt;
    }

    /** The one this builder made and is waiting on, or null. */
    @Nullable
    public Propuesta de(UUID constructor) {
        return porConstructor.get(constructor);
    }

    @Nullable
    public Propuesta porId(String id) {
        for (Propuesta p : porConstructor.values()) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return null;
    }

    public Collection<Propuesta> todas() {
        return Collections.unmodifiableCollection(porConstructor.values());
    }

    /** A builder has one proposal at a time: this one takes the place of its last. */
    public void poner(Propuesta p) {
        porConstructor.put(p.constructor, p);
        markDirty();
    }

    public void quitar(Propuesta p) {
        porConstructor.remove(p.constructor, p);
        markDirty();
    }

    public void cambio() {
        markDirty();
    }

    /** Those left a whole day without a word: dropped, and returned so their ghosts go too. */
    public List<Propuesta> caducar(long now) {
        List<Propuesta> out = new ArrayList<>();
        for (Propuesta p : List.copyOf(porConstructor.values())) {
            if (now - p.mostrada() > CADUCA || now < p.creada) {
                porConstructor.remove(p.constructor);
                out.add(p);
            }
        }
        if (!out.isEmpty()) {
            markDirty();
        }
        return out;
    }

    /** One house offered: the draft, its exact plan, its place. */
    public static final class Propuesta {
        public final String id;
        public final UUID constructor;
        public final String constructorNombre;
        /** The only one who can approve it. */
        public final UUID para;
        public final String paraNombre;
        public final long creada;
        private CatalogoPlanos.Borrador borrador;
        private Plano plano;
        private BlockPos origen;
        private Direction frente;
        private long mostrada;
        /** Bumped every time it changes or moves: its ghost is sent again. */
        private int version;
        private boolean pendienteSitio;
        @Nullable
        private Obra vista;

        public Propuesta(String id, UUID constructor, String constructorNombre, UUID para, String paraNombre, CatalogoPlanos.Borrador borrador,
                         BlockPos origen, Direction frente, long now) {
            this(id, constructor, constructorNombre, para, paraNombre, borrador, borrador.plano(), origen, frente, now, now, 0);
        }

        private Propuesta(String id, UUID constructor, String constructorNombre, UUID para, String paraNombre, CatalogoPlanos.Borrador borrador,
                          Plano plano, BlockPos origen, Direction frente, long creada, long mostrada, int version) {
            this.id = id;
            this.constructor = constructor;
            this.constructorNombre = constructorNombre == null ? "" : constructorNombre;
            this.para = para;
            this.paraNombre = paraNombre == null ? "" : paraNombre;
            this.borrador = borrador;
            this.plano = plano;
            this.origen = origen.toImmutable();
            this.frente = frente;
            this.creada = creada;
            this.mostrada = mostrada;
            this.version = version;
        }

        public CatalogoPlanos.Borrador borrador() {
            return borrador;
        }

        /** The exact plan offered: the one an approval builds, the same object, revision and hash. */
        public Plano plano() {
            return plano;
        }

        public boolean aMedida() {
            return EdicionPlano.ID.equals(plano.id);
        }

        public Plano.Madera madera() {
            return borrador.madera();
        }

        public BlockPos origen() {
            return origen;
        }

        public Direction frente() {
            return frente;
        }

        public long mostrada() {
            return mostrada;
        }

        public int version() {
            return version;
        }

        public boolean pendienteSitio() {
            return pendienteSitio;
        }

        public String llaveVista() {
            return (pendienteSitio ? PARCELA : PREFIJO) + id;
        }

        public void pedirSitio(CatalogoPlanos.Borrador nuevo, long now) {
            pendienteSitio = true;
            cambiar(nuevo, origen, frente, now);
        }

        public void pedirSitio(long now) {
            pendienteSitio = true;
            cambiarPlano(plano, origen, frente, now);
        }

        public void fijarSitio(BlockPos origen, Direction frente, long now) {
            pendienteSitio = false;
            cambiarPlano(plano, origen, frente, now);
        }

        /** Shown again, or talked about: a bare yes answers it for a while more. */
        public void mostrada(long now) {
            mostrada = now;
        }

        /** Whether a bare "sí" or "no" now is an answer to it. */
        public boolean reciente(long now) {
            return now >= mostrada && now - mostrada <= RESPUESTA;
        }

        /** Another draft, or another place for it: a new revision to look at. */
        public void cambiar(CatalogoPlanos.Borrador b, BlockPos origen, Direction frente, long now) {
            this.borrador = b;
            cambiarPlano(b.plano(), origen, frente, now);
        }

        public void cambiarPlano(Plano plano, BlockPos origen, Direction frente, long now) {
            this.plano = plano;
            this.origen = origen.toImmutable();
            this.frente = frente;
            this.mostrada = now;
            this.version++;
            this.vista = null;
        }

        /** The house as it would stand there, never put among the world's buildings: its cells for the ghost and the checks. */
        public Obra vista() {
            if (vista == null) {
                BlockPos at = pendienteSitio ? BlockPos.ORIGIN : origen;
                Direction direction = pendienteSitio ? Direction.NORTH : frente;
                Obra o = new Obra(llaveVista(), constructor, constructorNombre, at, direction, borrador.madera(), plano);
                o.para = para;
                o.paraNombre = paraNombre;
                o.version = version;
                vista = o;
            }
            return vista;
        }

        public String donde() {
            return vista().donde();
        }

        /** "una cabaña de troncos de abeto en 10 64 -3, con techo alto (revisión 2)". */
        public String resumen(boolean es) {
            Plano p = plano;
            if (aMedida()) {
                return (es ? "una construcción a medida, revisión " : "a custom build, revision ") + p.revision
                        + (pendienteSitio ? (es ? ", pendiente de parcela" : ", waiting for a plot") : (es ? " en " : " at ") + donde());
            }
            if (pendienteSitio) {
                return es ? "una " + p.nombre + " de " + madera().es + " pendiente de parcela"
                        : "a " + p.nombreEn + " in " + madera().en + " waiting for a plot";
            }
            String techo = borrador.cubierta() == ModelosCasa.Cubierta.DOS_AGUAS ? "" : (es ? ", con " + borrador.cubierta().es : ", with a " + borrador.cubierta().en);
            return es ? "una " + p.nombre + " de " + madera().es + " en " + donde() + techo + " (revisión " + p.revision + ")"
                    : "a " + p.nombreEn + " in " + madera().en + " at " + donde() + techo + " (revision " + p.revision + ")";
        }

        NbtCompound guardar() {
            NbtCompound n = new NbtCompound();
            n.putString("id", id);
            n.putUuid("constructor", constructor);
            n.putString("constructorNombre", constructorNombre);
            n.putUuid("para", para);
            n.putString("paraNombre", paraNombre);
            n.putString("modelo", borrador.modelo().id());
            n.putString("madera", borrador.madera().name());
            n.putString("cubierta", borrador.cubierta().name());
            if (borrador.balcon() != null) {
                n.putString("balcon", borrador.balcon().name());
            }
            n.putLongArray("abiertas", borrador.abiertas().stream().mapToLong(BlockPos::asLong).toArray());
            n.putLongArray("cerradas", borrador.cerradas().stream().mapToLong(BlockPos::asLong).toArray());
            n.putInt("revision", borrador.revision());
            n.put("plano", PlanoCodec.guardar(plano));
            n.putLong("origen", origen.asLong());
            n.putString("frente", frente.asString());
            n.putLong("creada", creada);
            n.putLong("mostrada", mostrada);
            n.putInt("version", version);
            n.putBoolean("pendienteSitio", pendienteSitio);
            return n;
        }

        /**
         * Read back, or null: the draft is drawn again and must give exactly the plan that was saved (same hash), else
         * the catalogue changed under it and the builder will offer it again rather than build something not shown.
         */
        @Nullable
        static Propuesta leer(NbtCompound n) {
            try {
                ModelosCasa.Modelo modelo = ModelosCasa.Modelo.de(n.getString("modelo"));
                Direction frente = Direction.byName(n.getString("frente"));
                if (modelo == null || frente == null || frente.getAxis().isVertical() || !n.contains("plano", NbtElement.COMPOUND_TYPE)) {
                    throw new IllegalArgumentException("incomplete proposal");
                }
                List<BlockPos> abiertas = new ArrayList<>();
                for (long l : n.getLongArray("abiertas")) {
                    abiertas.add(BlockPos.fromLong(l));
                }
                List<BlockPos> cerradas = new ArrayList<>();
                for (long l : n.getLongArray("cerradas")) {
                    cerradas.add(BlockPos.fromLong(l));
                }
                CambioCasa.Lado balcon = n.contains("balcon", NbtElement.STRING_TYPE)
                        ? CambioCasa.Lado.valueOf(n.getString("balcon")) : null;
                CatalogoPlanos.Borrador b = new CatalogoPlanos.Borrador(modelo, Plano.Madera.valueOf(n.getString("madera")),
                        ModelosCasa.Cubierta.valueOf(n.getString("cubierta")), abiertas, cerradas, n.getInt("revision"), balcon);
                Plano guardado = PlanoCodec.leer(n.getCompound("plano"));
                Plano dibujado = EdicionPlano.ID.equals(guardado.id) ? guardado : b.plano();
                if (!guardado.hash().equals(dibujado.hash())) {
                    throw new IllegalArgumentException("the catalogue draws another plan for this draft now");
                }
                Propuesta p = new Propuesta(n.getString("id"), n.getUuid("constructor"), n.getString("constructorNombre"), n.getUuid("para"),
                        n.getString("paraNombre"), b, dibujado, BlockPos.fromLong(n.getLong("origen")), frente, n.getLong("creada"),
                        n.getLong("mostrada"), n.getInt("version"));
                p.pendienteSitio = n.getBoolean("pendienteSitio");
                return p;
            } catch (RuntimeException e) {
                MCA.LOGGER.warn("[Tropeles] propuesta de casa {} descartada al leerla: {}", n.getString("id"), e.toString());
                return null;
            }
        }
    }
}
