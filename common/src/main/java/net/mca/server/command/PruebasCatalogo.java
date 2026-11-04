package net.mca.server.command;

import net.mca.MCA;
import net.mca.entity.ai.obra.CatalogoPlanos;
import net.mca.entity.ai.obra.ModelosCasa;
import net.mca.entity.ai.obra.Obra;
import net.mca.entity.ai.obra.Plano;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * C02 checks: the house catalogue. One command, {@code mca-prueba catalogo}, six terminal results (see {@link #NOMBRES}).
 * Five read plans only; {@code catalogo-colocacion} sets every house, under every roof it takes, down in the world in
 * each of the four ways it can face, in a builder's order (the frame bottom up, then what goes on it), and reads the
 * world back. Arena x 31000, z 31000, y 100. None of this shows a villager building a house: that is still to prove.
 */
final class PruebasCatalogo {
    static final List<String> NOMBRES = List.of("catalogo-casas", "catalogo-orientaciones", "catalogo-limites", "catalogo-edicion", "catalogo-obra-fija",
            "catalogo-colocacion");

    private static final String CABANA = ModelosCasa.Modelo.CABANA.id();
    private static final String LARGA = ModelosCasa.Modelo.LARGA.id();

    private PruebasCatalogo() {
    }

    /** {@code mca-prueba catalogo}: the five plan checks at once, then the placing in the world, tick by tick. */
    static void run(ServerWorld world, String which) {
        if (!"catalogo".equals(which)) {
            return;
        }
        seguro("catalogo-casas", PruebasCatalogo::casas);
        seguro("catalogo-orientaciones", PruebasCatalogo::orientaciones);
        seguro("catalogo-limites", PruebasCatalogo::limites);
        seguro("catalogo-edicion", PruebasCatalogo::edicion);
        seguro("catalogo-obra-fija", PruebasCatalogo::obraFija);
        Pruebas.start(new Colocacion(world));
    }

    /** One check that throws is that check failing, not the others going unrun. */
    private static void seguro(String name, Runnable check) {
        try {
            check.run();
        } catch (RuntimeException e) {
            MCA.LOGGER.error("PRUEBA FALLA " + name + ": excepción", e);
            Pruebas.check(name, false, "excepción: " + e);
        }
    }

    private static Plano plano(CatalogoPlanos.Ficha f, ModelosCasa.Cubierta c, Plano.Madera madera) {
        CatalogoPlanos.Borrador b = CatalogoPlanos.Borrador.de(f.modelo(), madera);
        if (c == ModelosCasa.Cubierta.DOS_AGUAS) {
            return b.plano();
        }
        CatalogoPlanos.Resultado r = CatalogoPlanos.cubierta(b, c);
        if (!r.ok() || r.plano() == null) {
            throw new IllegalStateException(f.id() + " no toma " + c + ": " + r.porQue());
        }
        return r.plano();
    }

    @Nullable
    private static Plano.Pieza en(Plano p, BlockPos rel) {
        for (Plano.Pieza x : p.piezas) {
            if (x.rel().equals(rel)) {
                return x;
            }
        }
        return null;
    }

    private static List<String> ids(List<CatalogoPlanos.Ficha> fichas) {
        List<String> out = new ArrayList<>();
        for (CatalogoPlanos.Ficha f : fichas) {
            out.add(f.id());
        }
        return out;
    }

    /**
     * Three houses of their own shape (ids, hashes and measures all different), each one sound as drawn; the first
     * house is {@link Plano#casa} itself and the roof module draws its roof cell for cell; a wood is a palette (same
     * cells and kinds, other blocks), not another design.
     */
    private static void casas() {
        List<CatalogoPlanos.Ficha> fichas = CatalogoPlanos.fichas();
        Set<String> ids = new HashSet<>();
        Set<String> hashes = new HashSet<>();
        Set<String> formas = new HashSet<>();
        StringBuilder d = new StringBuilder();
        boolean sanas = true;
        for (CatalogoPlanos.Ficha f : fichas) {
            Plano p = CatalogoPlanos.plano(f.id(), Plano.Madera.ROBLE);
            ModelosCasa.Revision r = ModelosCasa.revisar(p, Direction.NORTH);
            sanas &= r.ok() && p.id.equals(f.id()) && p.revision == f.revision();
            ids.add(f.id());
            hashes.add(p.hash());
            formas.add(f.ancho() + "x" + f.fondo() + "x" + f.alto() + "/" + f.bloques() + "/" + f.pisos());
            d.append(f.id()).append(": ").append(f.nombre()).append(", ").append(f.pisos()).append(" piso(s), ").append(f.ancho()).append('x').append(f.fondo())
                    .append('x').append(f.alto()).append(", ").append(f.bloques()).append(" bloques, techos ").append(f.cubiertas()).append(r.ok() ? "" : " MAL: " + r.resumen())
                    .append("; ");
        }
        Plano primera = Plano.casa(Plano.Madera.ROBLE);
        Plano delCatalogo = CatalogoPlanos.plano(primera.id, Plano.Madera.ROBLE);
        boolean misma = delCatalogo != null && delCatalogo.hash().equals(primera.hash());
        CatalogoPlanos.Diferencia rehecha = CatalogoPlanos.diferencia(primera, ModelosCasa.primeraRehecha(Plano.Madera.ROBLE));
        Plano roble = CatalogoPlanos.plano(CABANA, Plano.Madera.ROBLE);
        Plano cerezo = CatalogoPlanos.plano(CABANA, Plano.Madera.CEREZO);
        CatalogoPlanos.Diferencia madera = CatalogoPlanos.diferencia(roble, cerezo);
        boolean paleta = madera.agregadas() == 0 && madera.quitadas() == 0 && madera.cambiadas() > 0 && madera.materiales().isEmpty()
                && madera.cambios().stream().allMatch(c -> c.antes() != null && c.despues() != null && c.antes().rol() == c.despues().rol())
                && roble.id.equals(cerezo.id) && !roble.hash().equals(cerezo.hash());
        boolean ok = fichas.size() == 3 && ids.size() == 3 && hashes.size() == 3 && formas.size() == 3 && sanas && misma && rehecha.vacia() && paleta;
        Pruebas.check("catalogo-casas", ok, d + "la primera es Plano.casa: " + misma + "; su techo rehecho difiere en: " + rehecha.resumen(true)
                + "; cabaña de cerezo frente a la de roble: " + madera.resumen(true));
    }

    /**
     * Every house under every roof it takes, turned to each of the four fronts the way a work is set down: still sound,
     * and the same house (cells, blocks, floor reached, levels; width and depth swapped when turned a quarter). Every
     * design takes at least one roof besides its own.
     */
    private static void orientaciones() {
        int vistas = 0;
        List<String> malas = new ArrayList<>();
        for (CatalogoPlanos.Ficha f : CatalogoPlanos.fichas()) {
            if (f.cubiertas().size() < 2) {
                malas.add(f.id() + " no toma otro techo que el suyo");
            }
            for (ModelosCasa.Cubierta c : f.cubiertas()) {
                Plano p = plano(f, c, Plano.Madera.ROBLE);
                ModelosCasa.Revision norte = ModelosCasa.revisar(p, Direction.NORTH);
                for (Direction fr : Direction.Type.HORIZONTAL) {
                    ModelosCasa.Revision r = ModelosCasa.revisar(p, fr);
                    vistas++;
                    boolean cuarto = fr.getAxis() == Direction.Axis.X;
                    boolean igual = r.ok() && r.celdas() == norte.celdas() && r.bloques() == norte.bloques() && r.suelo() == norte.suelo()
                            && r.alcanzado() == norte.alcanzado() && r.pisos() == norte.pisos() && r.alto() == norte.alto()
                            && (cuarto ? r.ancho() == norte.fondo() && r.fondo() == norte.ancho() : r.ancho() == norte.ancho() && r.fondo() == norte.fondo());
                    if (!igual) {
                        malas.add(f.id() + " " + c + " " + fr + ": " + r.resumen());
                    }
                }
            }
        }
        Pruebas.check("catalogo-orientaciones", malas.isEmpty() && vistas >= 24, vistas + " vistas" + (malas.isEmpty() ? ", todas sanas e iguales" : "; " + malas));
    }

    /**
     * Within the limits; the plot filter (either way round, floors); unknown ids; roofs the walls can't carry; windows
     * where there is no wall to open, too many at once, or that would leave a torch hanging: each refused, with why,
     * and the draft as it was.
     */
    private static void limites() {
        List<String> malas = new ArrayList<>();
        for (CatalogoPlanos.Ficha f : CatalogoPlanos.fichas()) {
            for (ModelosCasa.Cubierta c : f.cubiertas()) {
                ModelosCasa.Revision r = ModelosCasa.revisar(plano(f, c, Plano.Madera.ROBLE), Direction.NORTH);
                if (r.celdas() > ModelosCasa.CELDAS_MAX || r.ancho() > ModelosCasa.EJE_MAX || r.fondo() > ModelosCasa.EJE_MAX || r.alto() > ModelosCasa.ALTO_MAX) {
                    malas.add(f.id() + " " + c + " fuera de límites: " + r.resumen());
                }
            }
        }
        String primera = ModelosCasa.Modelo.DOS_PISOS.id();
        Map<String, List<String>> filtros = Map.of(
                "7x7", ids(CatalogoPlanos.buscar(CatalogoPlanos.Filtro.parcela(7, 7))),
                "7x12", ids(CatalogoPlanos.buscar(CatalogoPlanos.Filtro.parcela(7, 12))),
                "12x7 un piso", ids(CatalogoPlanos.buscar(new CatalogoPlanos.Filtro(12, 7, ModelosCasa.ALTO_MAX, 1, null))),
                "dos pisos", ids(CatalogoPlanos.buscar(new CatalogoPlanos.Filtro(64, 64, 64, 2, Plano.Madera.MANGLE))),
                "6x6", ids(CatalogoPlanos.buscar(CatalogoPlanos.Filtro.parcela(6, 6))));
        Map<String, List<String>> esperado = Map.of("7x7", List.of(CABANA), "7x12", sorted(List.of(CABANA, LARGA, primera)), "12x7 un piso",
                sorted(List.of(CABANA, LARGA)), "dos pisos", List.of(primera), "6x6", List.of());
        if (!filtros.equals(esperado)) {
            malas.add("filtros " + filtros + " en vez de " + esperado);
        }
        if (CatalogoPlanos.ficha("tropeles:casa/torre") != null || CatalogoPlanos.plano("tropeles:casa/torre", Plano.Madera.ROBLE) != null
                || CatalogoPlanos.borrador("", Plano.Madera.ROBLE) != null) {
            malas.add("un id que no existe dio algo");
        }
        CatalogoPlanos.Borrador cab = CatalogoPlanos.Borrador.de(ModelosCasa.Modelo.CABANA, Plano.Madera.ROBLE);
        CatalogoPlanos.Borrador lar = CatalogoPlanos.Borrador.de(ModelosCasa.Modelo.LARGA, Plano.Madera.ROBLE);
        CatalogoPlanos.Borrador dos = CatalogoPlanos.Borrador.de(ModelosCasa.Modelo.DOS_PISOS, Plano.Madera.ROBLE);
        String h = cab.plano().hash();
        no(malas, "techo alto en la de dos pisos", CatalogoPlanos.cubierta(dos, ModelosCasa.Cubierta.ALTO), "14 bloques");
        no(malas, "techo girado en la larga", CatalogoPlanos.cubierta(lar, ModelosCasa.Cubierta.TRAVES), "11 bloques");
        no(malas, "el mismo techo", CatalogoPlanos.cubierta(cab, ModelosCasa.Cubierta.DOS_AGUAS), "ya tiene");
        no(malas, "ventana en la esquina", CatalogoPlanos.ventanas(cab, List.of(new BlockPos(0, 2, 0)), List.of()), "no hay pared");
        no(malas, "ventana en la puerta", CatalogoPlanos.ventanas(cab, List.of(new BlockPos(3, 1, 0)), List.of()), "no hay pared");
        no(malas, "ventana tras la mesa", CatalogoPlanos.ventanas(cab, List.of(new BlockPos(1, 1, 4)), List.of()), "no hay pared");
        no(malas, "ventana en el tabique", CatalogoPlanos.ventanas(lar, List.of(new BlockPos(1, 2, 5)), List.of()), "no hay pared");
        no(malas, "tres ventanas de una vez",
                CatalogoPlanos.ventanas(cab, List.of(new BlockPos(1, 1, 0), new BlockPos(2, 1, 0), new BlockPos(4, 1, 0)), List.of()), "por vez");
        no(malas, "ventana donde cuelga la antorcha del porche", CatalogoPlanos.ventanas(cab, List.of(new BlockPos(2, 2, 0)), List.of()), "antorcha");
        no(malas, "cerrar lo que no es ventana", CatalogoPlanos.ventanas(cab, List.of(), List.of(new BlockPos(2, 2, 0))), "no hay ventana");
        no(malas, "abrir y cerrar la misma", CatalogoPlanos.ventanas(cab, List.of(new BlockPos(1, 2, 0)), List.of(new BlockPos(1, 2, 0))), "misma");
        if (!cab.plano().hash().equals(h)) {
            malas.add("un cambio rechazado tocó el borrador");
        }
        Pruebas.check("catalogo-limites", malas.isEmpty(), malas.isEmpty() ? "límites, filtros " + filtros + " y 11 rechazos con su razón" : malas.toString());
    }

    private static List<String> sorted(List<String> l) {
        List<String> out = new ArrayList<>(l);
        out.sort(String::compareTo);
        return out;
    }

    private static void no(List<String> malas, String que, CatalogoPlanos.Resultado r, String razon) {
        if (r.ok()) {
            malas.add(que + " se aceptó");
        } else if (!r.porQue().contains(razon)) {
            malas.add(que + ": la razón fue «" + r.porQue() + "»");
        } else {
            MCA.LOGGER.info("[Tropeles] catálogo: {} se rechaza: {}", que, r.porQue());
        }
    }

    /**
     * Two windows opened in the cabin's back wall: exactly those two cells change (laid log to pane), +2 panes and -2
     * logs, same id, the next revision, another hash, the draft it came from untouched and the new one sound in all
     * four fronts. A steep roof on top of that: only the roof changes (nothing under the eaves) and the two windows
     * stay. Walling the two up again: the same house as the first, cell for cell.
     */
    private static void edicion() {
        List<String> malas = new ArrayList<>();
        CatalogoPlanos.Borrador b = CatalogoPlanos.Borrador.de(ModelosCasa.Modelo.CABANA, Plano.Madera.ROBLE);
        Plano base = b.plano();
        String h0 = base.hash();
        List<BlockPos> dos = List.of(new BlockPos(1, 2, 4), new BlockPos(5, 2, 4));
        CatalogoPlanos.Resultado v = CatalogoPlanos.ventanas(b, dos, List.of());
        if (!v.ok() || v.plano() == null || v.diferencia() == null) {
            Pruebas.check("catalogo-edicion", false, "abrir dos ventanas se rechazó: " + v.porQue());
            return;
        }
        CatalogoPlanos.Diferencia dv = v.diferencia();
        boolean justas = dv.cambios().size() == 2 && dv.cambios().stream().allMatch(c -> dos.contains(c.rel()) && c.antes() != null && c.despues() != null
                && c.antes().rol() == Plano.Rol.TRONCO && c.despues().rol() == Plano.Rol.VIDRIO);
        if (!justas || !dv.materiales().equals(Map.of(Plano.Rol.VIDRIO, 2, Plano.Rol.TRONCO, -2))) {
            malas.add("ventanas: " + dv.resumen(true));
        }
        if (!v.plano().id.equals(base.id) || v.plano().revision != base.revision + 1 || v.plano().hash().equals(h0) || !b.plano().hash().equals(h0)) {
            malas.add("revisión: id " + v.plano().id + ", rev " + v.plano().revision + " (base " + base.revision + "), hash nuevo " + !v.plano().hash().equals(h0)
                    + ", base intacta " + b.plano().hash().equals(h0));
        }
        for (Direction fr : Direction.Type.HORIZONTAL) {
            ModelosCasa.Revision r = ModelosCasa.revisar(v.plano(), fr);
            if (!r.ok()) {
                malas.add("con ventanas, " + fr + ": " + r.resumen());
            }
        }
        CatalogoPlanos.Resultado t = CatalogoPlanos.cubierta(v.borrador(), ModelosCasa.Cubierta.ALTO);
        int alero = ModelosCasa.Modelo.CABANA.techo - 1;
        if (!t.ok() || t.plano() == null || t.diferencia() == null) {
            malas.add("techo alto rechazado: " + t.porQue());
        } else {
            Plano alto = t.plano();
            if (!t.diferencia().cambios().stream().allMatch(c -> c.rel().getY() >= alero)) {
                malas.add("el techo alto cambió algo bajo el alero: " + t.diferencia().resumen(true));
            }
            if (!dos.stream().allMatch(p -> {
                Plano.Pieza x = en(alto, p);
                return x != null && x.rol() == Plano.Rol.VIDRIO;
            })) {
                malas.add("con el techo alto se perdieron las ventanas");
            }
        }
        CatalogoPlanos.Resultado vuelta = CatalogoPlanos.ventanas(v.borrador(), List.of(), dos);
        if (!vuelta.ok() || vuelta.plano() == null || !CatalogoPlanos.diferencia(base, vuelta.plano()).vacia() || !vuelta.borrador().abiertas().isEmpty()
                || !vuelta.borrador().cerradas().isEmpty()) {
            malas.add("al cerrarlas de nuevo no volvió a la cabaña: " + (vuelta.plano() == null ? vuelta.porQue() : CatalogoPlanos.diferencia(base, vuelta.plano()).resumen(true)));
        }
        Pruebas.check("catalogo-edicion", malas.isEmpty(), malas.isEmpty()
                ? "ventanas " + dv.resumen(true) + "; techo alto " + (t.diferencia() == null ? "?" : t.diferencia().resumen(true)) + "; vuelta sin diferencias"
                : malas.toString());
    }

    /**
     * A work keeps the plan it was set down with: a long house in spruce made a work facing east, then its draft
     * changed; the work's plan is the same object with the same hash, its cells are the plan turned and set down, and
     * its entrance is the plan's.
     */
    private static void obraFija() {
        CatalogoPlanos.Borrador b = CatalogoPlanos.Borrador.de(ModelosCasa.Modelo.LARGA, Plano.Madera.ABETO);
        Plano p = b.plano();
        BlockPos origen = new BlockPos(31000, 100, 31040);
        Obra o = new Obra("catalogo", UUID.randomUUID(), "Prueba", origen, Direction.EAST, Plano.Madera.ABETO, p);
        String h = o.plano().hash();
        CatalogoPlanos.Resultado r = CatalogoPlanos.cubierta(b, ModelosCasa.Cubierta.ALTO);
        BlockRotation g = Plano.giro(Direction.EAST);
        boolean celdas = o.celdas().size() == p.piezas.size();
        for (Plano.Pieza x : p.piezas) {
            Obra.Celda c = o.en(Plano.enMundo(origen, g, x.rel()));
            celdas &= c != null && c.rol() == x.rol() && c.state().equals(x.state().rotate(g));
        }
        boolean entrada = o.entrada().equals(Plano.enMundo(origen, g, p.entrada));
        boolean fija = r.ok() && r.plano() != null && o.plano() == p && o.plano().hash().equals(h) && !r.plano().hash().equals(h);
        Pruebas.check("catalogo-obra-fija", fija && celdas && entrada, "la obra conserva su plano: " + fija + "; sus celdas son el plano girado: " + celdas
                + "; su entrada es la del plano: " + entrada + " (" + o.entrada().toShortString() + ")");
    }

    /**
     * Sets every house, under every roof it takes, down in the world in each of the four fronts: the frame bottom up,
     * then the door, panes, torches and furniture bottom up, each block only where vanilla allows it right then (a door
     * on whole floor, a torch on its wall), the door the way a player's hand sets it (both halves). Then every cell is
     * read back: the plan's kind of block, the right way round, empty where it should be. One house a tick.
     */
    private static final class Colocacion extends Pruebas.Scenario {
        private static final BlockPos O = new BlockPos(31000, 100, 31000);
        private final List<Plano> planos = new ArrayList<>();
        private final List<String> malas = new ArrayList<>();
        private int i;
        private int bloques;
        /** What the door would be, set the way {@code Constructor.colocar} sets it today (null until a door is set). */
        @Nullable
        private BlockState comoConstructor;

        Colocacion(ServerWorld world) {
            super("catalogo-colocacion", world, 400);
            Pruebas.load(world, O, 24);
            int n = 0;
            for (CatalogoPlanos.Ficha f : CatalogoPlanos.fichas()) {
                for (ModelosCasa.Cubierta c : f.cubiertas()) {
                    planos.add(plano(f, c, Plano.Madera.values()[n++ % Plano.Madera.values().length]));
                }
            }
            Pruebas.fill(world, O.add(-16, -1, -16), O.add(16, -1, 16), Blocks.STONE.getDefaultState());
            Pruebas.fill(world, O.add(-16, 0, -16), O.add(16, 15, 16), Blocks.AIR.getDefaultState());
        }

        @Override
        @Nullable
        Boolean step() {
            if (ticks < 20) {
                return null;
            }
            List<Direction> frentes = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
            if (i >= planos.size() * frentes.size()) {
                boolean ok = malas.isEmpty() && i >= 24;
                Pruebas.check(name, ok, (ok ? i + " casas puestas (" + bloques + " bloques), cada celda como en su plano" : malas.toString())
                        + "; aviso para C03: una puerta procesada antes de su mitad de arriba, como en Constructor.colocar, sale " + comoConstructor);
                return ok;
            }
            Plano p = planos.get(i / frentes.size());
            Direction fr = frentes.get(i % frentes.size());
            i++;
            colocar(p, fr);
            for (Plano.Pieza x : p.piezas) {
                // FORCE_STATE: no shape updates, so nothing pops off as an item while the house comes down
                world.setBlockState(Plano.enMundo(O, Plano.giro(fr), x.rel()), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
            }
            return null;
        }

        private void colocar(Plano p, Direction fr) {
            BlockRotation g = Plano.giro(fr);
            int sinLugar = 0;
            String primera = null;
            for (int pasada = 0; pasada < 2; pasada++) {
                for (Plano.Pieza x : p.piezas) {
                    if (!x.rol().bloque() || x.rol().estructura != (pasada == 0)) {
                        continue;
                    }
                    BlockPos at = Plano.enMundo(O, g, x.rel());
                    BlockState s = x.state().rotate(g);
                    if (!world.getBlockState(at).isReplaceable() || !s.canPlaceAt(world, at)) {
                        sinLugar++;
                        if (primera == null) {
                            primera = x.rol() + " en " + ModelosCasa.texto(x.rel());
                        }
                        continue;
                    }
                    if (x.rol() == Plano.Rol.PUERTA) {
                        if (comoConstructor == null) {
                            comoConstructor = Block.postProcessState(s, world, at);
                            MCA.LOGGER.info("[Tropeles] catálogo: una puerta puesta como en Constructor.colocar (postProcessState antes de su mitad de arriba) sale {}",
                                    comoConstructor);
                        }
                        world.setBlockState(at, s, Block.NOTIFY_LISTENERS);
                        world.setBlockState(at.up(), s.with(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.NOTIFY_LISTENERS);
                    } else {
                        world.setBlockState(at, Block.postProcessState(s, world, at), Block.NOTIFY_LISTENERS);
                    }
                    bloques++;
                }
            }
            int distintas = 0;
            String cual = null;
            for (Plano.Pieza x : p.piezas) {
                BlockPos at = Plano.enMundo(O, g, x.rel());
                BlockState planned = x.state().rotate(g);
                BlockState actual = world.getBlockState(at);
                boolean puerta = x.rol() == Plano.Rol.PUERTA || x.rol() == Plano.Rol.PUERTA_ARRIBA;
                boolean ok = Plano.hecho(x.rol(), planned, actual) && (!puerta || actual.get(DoorBlock.FACING) == planned.get(DoorBlock.FACING)
                        && actual.get(DoorBlock.HINGE) == planned.get(DoorBlock.HINGE));
                if (!ok) {
                    distintas++;
                    if (cual == null) {
                        cual = x.rol() + " en " + ModelosCasa.texto(x.rel()) + ": " + actual;
                    }
                }
            }
            BlockPos puerta = null;
            for (Plano.Pieza x : p.piezas) {
                if (x.rol() == Plano.Rol.PUERTA) {
                    puerta = Plano.enMundo(O, g, x.rel());
                }
            }
            BlockState door = puerta == null ? Blocks.AIR.getDefaultState() : world.getBlockState(puerta);
            boolean frente = door.contains(DoorBlock.FACING) && puerta.offset(door.get(DoorBlock.FACING).getOpposite()).equals(Plano.enMundo(O, g, p.entrada));
            if (sinLugar > 0 || distintas > 0 || !frente) {
                malas.add(p.nombre + " " + fr + ": " + sinLugar + " sin lugar (" + primera + "), " + distintas + " distintas (" + cual + "), puerta frente a la entrada "
                        + frente);
            }
        }

        @Override
        String state() {
            return i + " de " + planos.size() * 4 + " puestas; " + malas;
        }

        @Override
        void cleanup() {
            Pruebas.fill(world, O.add(-16, 0, -16), O.add(16, 15, 16), Blocks.AIR.getDefaultState());
        }
    }
}
