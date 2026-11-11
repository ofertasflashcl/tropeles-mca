package net.mca.entity.ai.mente;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** The villager's own experience. Keys describe situations and moves, never a position or a world seed. */
public final class Aprendizaje {
    public enum Agua {
        ABIERTA("agua con espacio arriba", "water with room above"),
        TECHO("agua bajo techo", "water under a ceiling"),
        SUPERFICIE("agua con la cabeza fuera", "water with head above");

        final String es;
        final String en;

        Agua(String es, String en) {
            this.es = es;
            this.en = en;
        }
    }

    public enum Maniobra {
        SUBIR("subir primero", "swim up first"),
        LADO("salir de lado", "swim sideways first");

        final String es;
        final String en;

        Maniobra(String es, String en) {
            this.es = es;
            this.en = en;
        }
    }

    public enum Desenlace {ORILLA, ATASCO, AIRE_BAJO}

    public record Balance(int exitos, int intentos) {
        double estimacion() {
            return (exitos + 1.0) / (intentos + 2.0);
        }
    }

    private record EncargoVisto(Encargo.Tipo tipo, Encargo.Resultado resultado, Encargo.Fin causa, int hecho, int pedido) {
    }

    private static final int VERSION = 1;
    private static final int VENTANA = 8;
    private static final int MASCARA = (1 << VENTANA) - 1;
    private static final Agua[] CONTEXTOS = Agua.values();
    private static final Maniobra[] MANIOBRAS = Maniobra.values();
    /** Per context and move: high byte counts samples, bit zero is the newest outcome. */
    private final int[][] muestras = new int[CONTEXTOS.length][MANIOBRAS.length];
    @Nullable
    private EncargoVisto encargo;
    @Nullable
    private NbtCompound versionFutura;

    public void registrar(Agua contexto, Maniobra maniobra, Desenlace resultado) {
        if (versionFutura != null) return;
        int[] fila = muestras[contexto.ordinal()];
        int anterior = fila[maniobra.ordinal()];
        int n = Math.min(VENTANA, (anterior >>> VENTANA) + 1);
        int bits = ((anterior << 1) | (resultado == Desenlace.ORILLA ? 1 : 0)) & MASCARA;
        fila[maniobra.ordinal()] = (n << VENTANA) | bits;
    }

    public Balance balance(Agua contexto, Maniobra maniobra) {
        int serie = muestras[contexto.ordinal()][maniobra.ordinal()];
        return new Balance(Integer.bitCount(serie & MASCARA), serie >>> VENTANA);
    }

    /** Keep the ordinary route when evidence is scarce or the most recent attempt contradicted it. */
    @Nullable
    public Maniobra preferida(Agua contexto) {
        Balance subir = balance(contexto, Maniobra.SUBIR);
        Balance lado = balance(contexto, Maniobra.LADO);
        Maniobra mejor = subir.estimacion() > lado.estimacion() ? Maniobra.SUBIR : Maniobra.LADO;
        Balance propia = mejor == Maniobra.SUBIR ? subir : lado;
        Balance otra = mejor == Maniobra.SUBIR ? lado : subir;
        int serie = muestras[contexto.ordinal()][mejor.ordinal()];
        return propia.intentos >= 2 && propia.estimacion() >= 0.65 && propia.estimacion() - otra.estimacion() >= 0.14
                && (serie & 1) != 0 ? mejor : null;
    }

    void finEncargo(Encargo e, Encargo.Resultado resultado, Encargo.Fin causa, boolean efecto) {
        if (versionFutura == null && valido(resultado)) {
            int hecho = efecto ? Math.max(0, Math.min(e.hecho, e.cuanto)) : 0;
            encargo = new EncargoVisto(e.tipo, resultado, causa, hecho, e.cuanto);
        }
    }

    private static boolean valido(Encargo.Resultado resultado) {
        return switch (resultado) {
            case HECHO, PARCIAL, BLOQUEADO, FALLIDO -> true;
            default -> false;
        };
    }

    /** Short evidence for the language mind. A successful move elsewhere is not a route here. */
    public String paraModelo(boolean es) {
        List<String> hechos = new ArrayList<>();
        for (Agua contexto : CONTEXTOS) {
            for (Maniobra maniobra : MANIOBRAS) {
                Balance balance = balance(contexto, maniobra);
                if (balance.intentos > 0) {
                    hechos.add((es ? contexto.es + ", " + maniobra.es : contexto.en + ", " + maniobra.en)
                            + " " + balance.exitos + "/" + balance.intentos
                            + (es ? " llegadas a orilla" : " arrivals on dry ground"));
                }
            }
        }
        if (encargo != null) {
            hechos.add((es ? "último encargo " + encargo.tipo.palabra : "last errand " + encargo.tipo.english)
                    + " " + encargo.hecho + "/" + encargo.pedido + " (" + encargo.resultado + ", " + encargo.causa + ")");
        }
        return String.join(". ", hechos);
    }

    public NbtCompound guardar() {
        if (versionFutura != null) return versionFutura.copy();
        NbtCompound nbt = new NbtCompound();
        for (Agua contexto : CONTEXTOS) {
            for (Maniobra maniobra : MANIOBRAS) {
                int serie = muestras[contexto.ordinal()][maniobra.ordinal()];
                if (serie != 0) nbt.putInt(clave(contexto, maniobra), serie);
            }
        }
        if (encargo != null) {
            NbtCompound e = new NbtCompound();
            e.putString("tipo", encargo.tipo.name());
            e.putString("resultado", encargo.resultado.name());
            e.putString("causa", encargo.causa.name());
            e.putInt("hecho", encargo.hecho);
            e.putInt("pedido", encargo.pedido);
            nbt.put("encargo", e);
        }
        if (!nbt.isEmpty()) nbt.putInt("version", VERSION);
        return nbt;
    }

    public void cargar(NbtCompound nbt) {
        versionFutura = nbt.getInt("version") > VERSION ? nbt.copy() : null;
        boolean conocida = nbt.getInt("version") == VERSION;
        for (Agua contexto : CONTEXTOS) {
            for (Maniobra maniobra : MANIOBRAS) {
                int serie = conocida ? nbt.getInt(clave(contexto, maniobra)) : 0;
                int n = serie >>> VENTANA;
                muestras[contexto.ordinal()][maniobra.ordinal()] = n <= VENTANA && (serie & MASCARA) < (1 << n) ? serie : 0;
            }
        }
        encargo = conocida && nbt.contains("encargo", NbtElement.COMPOUND_TYPE) ? leerEncargo(nbt.getCompound("encargo")) : null;
    }

    @Nullable
    private static EncargoVisto leerEncargo(NbtCompound nbt) {
        try {
            Encargo.Tipo tipo = Encargo.Tipo.valueOf(nbt.getString("tipo"));
            Encargo.Resultado resultado = Encargo.Resultado.valueOf(nbt.getString("resultado"));
            Encargo.Fin causa = Encargo.Fin.valueOf(nbt.getString("causa"));
            int hecho = nbt.getInt("hecho");
            int pedido = nbt.getInt("pedido");
            return valido(resultado) && pedido > 0 && hecho >= 0 && hecho <= pedido
                    ? new EncargoVisto(tipo, resultado, causa, hecho, pedido) : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String clave(Agua contexto, Maniobra maniobra) {
        return contexto.name() + "_" + maniobra.name();
    }
}
