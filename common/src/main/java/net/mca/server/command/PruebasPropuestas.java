package net.mca.server.command;

import net.mca.Config;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Chore;
import net.mca.entity.ai.chatAI.Cerebros;
import net.mca.entity.ai.chatAI.OpenAIChatAI;
import net.mca.entity.ai.mente.Voluntad;
import net.mca.entity.ai.obra.CambioCasa;
import net.mca.entity.ai.obra.CatalogoPlanos;
import net.mca.entity.ai.obra.Constructor;
import net.mca.entity.ai.obra.ModelosCasa;
import net.mca.entity.ai.obra.Obra;
import net.mca.entity.ai.obra.ObrasMundo;
import net.mca.entity.ai.obra.Plano;
import net.mca.entity.ai.obra.Propuestas;
import net.mca.entity.ai.obra.Proyeccion;
import net.mca.item.MarcadorParcela;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Pair;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * C03b checks: a house is proposed, changed and approved by the player's own words. One command, {@code mca-prueba
 * propuestas}, with the results in {@link #NOMBRES}; {@code mca-prueba propuestas-reinicio} reads in the second
 * process what {@code propuestas-preparar} left. Arena x 33000, z 33000, y 100. The chat itself (a model, a packet from
 * a real client, the golden ghost on screen) is not here: the checks call what the chat calls.
 */
final class PruebasPropuestas {
    static final List<String> NOMBRES = List.of("propuestas-palabras", "propuestas-crear", "propuestas-editar", "propuestas-aprobar",
            "propuestas-cancelar", "propuestas-balcon", "propuestas-guardado", "propuestas-preparar");
    private static final List<String> EN_ARENA = List.of("propuestas-crear", "propuestas-editar", "propuestas-aprobar", "propuestas-cancelar", "propuestas-balcon");

    private static final BlockPos O = new BlockPos(33000, 100, 33000);
    private static final UUID REINICIO = UUID.fromString("c03b0000-0000-4000-8000-00000000c03b");
    private static final UUID SIN_PARCELA = UUID.fromString("c03b0000-0000-4000-8000-00000000c03c");
    private static final UUID JUGADOR = UUID.fromString("c03b0000-0000-4000-8000-0000000b3a1a");
    private static final BlockPos ORIGEN_REINICIO = new BlockPos(33200, 100, 33200);

    private PruebasPropuestas() {
    }

    static void run(ServerWorld world, String which) {
        if ("propuestas-reinicio".equals(which)) {
            caso(which, () -> reinicio(world));
            return;
        }
        if (!"propuestas".equals(which)) {
            return;
        }
        caso("propuestas-palabras", PruebasPropuestas::palabras);
        boolean tools = Config.getInstance().villagerChatAIUseTools;
        Config.getInstance().villagerChatAIUseTools = true;
        Arena a = null;
        try {
            a = new Arena(world);
            Arena x = a;
            caso("propuestas-crear", () -> crear(x));
            caso("propuestas-editar", () -> editar(x));
            caso("propuestas-aprobar", () -> aprobar(x));
            caso("propuestas-cancelar", () -> cancelar(x));
            caso("propuestas-balcon", () -> balcon(x));
        } catch (RuntimeException e) {
            for (String n : EN_ARENA) {
                Pruebas.check(n, false, "no se pudo armar la arena: " + e);
            }
        } finally {
            if (a != null) {
                a.limpiar();
            }
            Config.getInstance().villagerChatAIUseTools = tools;
        }
        caso("propuestas-guardado", PruebasPropuestas::guardado);
        caso("propuestas-preparar", () -> preparar(world));
    }

    private static void caso(String name, Runnable check) {
        try {
            check.run();
            Pruebas.check(name, true, "invariantes verificadas");
        } catch (RuntimeException e) {
            Pruebas.check(name, false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void exige(boolean condition, String why) {
        if (!condition) {
            throw new IllegalStateException(why);
        }
    }

    /** A flat field, a player who asks, another who does not, and two connected builders. */
    private static final class Arena {
        final ServerWorld world;
        final ServerPlayerEntity pide;
        final ServerPlayerEntity otro;
        final ServerPlayerEntity tercero;
        final VillagerEntityMCA v;
        final VillagerEntityMCA v2;

        Arena(ServerWorld world) {
            this.world = world;
            Pruebas.load(world, O, 40);
            Pruebas.fill(world, O.add(-30, -2, -30), O.add(30, -1, 30), Blocks.GRASS_BLOCK.getDefaultState());
            Pruebas.fill(world, O.add(-30, 0, -30), O.add(30, 15, 30), Blocks.AIR.getDefaultState());
            // "aquí" is where they look: down at the field a few blocks ahead, the one asking south, the other north
            pide = Pruebas.fakePlayer(world, "Pide_Casa", Vec3d.ofBottomCenter(O.south(10)));
            pide.refreshPositionAndAngles(pide.getX(), pide.getY(), pide.getZ(), 0.0F, 35.0F);
            otro = Pruebas.fakePlayer(world, "Otro_Jugador", Vec3d.ofBottomCenter(O.south(10).west(12)));
            tercero = Pruebas.fakePlayer(world, "Pide_Otra", Vec3d.ofBottomCenter(O.north(14)));
            tercero.refreshPositionAndAngles(tercero.getX(), tercero.getY(), tercero.getZ(), 180.0F, 35.0F);
            v = Pruebas.villager(world, O.south(4).east(14), Direction.WEST, "Rafa Casa");
            v.getVillagerBrain().assignJob(Chore.PROSPECT, pide);
            v2 = Pruebas.villager(world, O.north(4).east(14), Direction.WEST, "Lia Casa");
            v2.getVillagerBrain().assignJob(Chore.PROSPECT, tercero);
        }

        Propuestas.Propuesta propuesta(VillagerEntityMCA x) {
            return Propuestas.get(world).de(x.getUuid());
        }

        Obra obra(VillagerEntityMCA x) {
            return ObrasMundo.get(world).de(x.getUuid());
        }

        void limpiar() {
            for (VillagerEntityMCA x : List.of(v, v2)) {
                Propuestas.Propuesta p = propuesta(x);
                if (p != null) {
                    Propuestas.get(world).quitar(p);
                    Proyeccion.borrarPropuesta(world, p);
                }
                Obra o = obra(x);
                if (o != null) {
                    ObrasMundo.get(world).quitar(o);
                    Proyeccion.borrar(world, o);
                }
                Constructor.olvidar(x.getUuid());
                Voluntad.olvidar(x);
                x.discard();
            }
        }
    }

    private static void palabras() {
        CambioCasa.Pedido p = CambioCasa.leer("ponle techo alto y dos ventanas a la izquierda");
        exige(p.cubierta() == ModelosCasa.Cubierta.ALTO && p.ventanas().equals(List.of(new CambioCasa.Ventanas(true, 2, CambioCasa.Lado.IZQUIERDA))),
                "Leyó mal techo y ventanas: " + p);
        exige(CambioCasa.leer("no abras ventanas atrás").vacio(), "Abrió ventanas que le dijeron que no");
        exige(CambioCasa.leer("no quiero una cabaña, mejor la casa larga").modelo() == ModelosCasa.Modelo.LARGA, "Tomó el diseño negado");
        exige(CambioCasa.leer("cierra las ventanas de atrás").ventanas().equals(List.of(new CambioCasa.Ventanas(false, 2, CambioCasa.Lado.ATRAS))),
                "Leyó mal cerrar ventanas");
        for (String si : List.of("sí", "¡Sí, dale!", "dale, hazla", "ok", "de acuerdo", "Rafa, sí", "go ahead")) {
            exige(CambioCasa.si(si, "Rafa Casa"), "No leyó un sí: " + si);
        }
        for (String no : List.of("sí, pero con techo alto", "¿sí?", "si no", "sí, ponle dos ventanas", "bueno, no sé", "no")) {
            exige(!CambioCasa.si(no, "Rafa Casa"), "Tomó como sí: " + no);
        }
        for (String no : List.of("no", "no gracias", "mejor no", "olvídalo")) {
            exige(CambioCasa.no(no, "Rafa Casa"), "No leyó un no: " + no);
        }
        exige(!CambioCasa.no("no, con techo alto", "Rafa Casa"), "Tomó un cambio como no");
        CatalogoPlanos.Borrador b = CatalogoPlanos.Borrador.de(ModelosCasa.Modelo.CABANA, Plano.Madera.ABETO);
        CambioCasa.Hecho h = CambioCasa.aplicar(b, p);
        CambioCasa.Hecho otra = CambioCasa.aplicar(b, p);
        exige(h.ok() && otra.ok() && h.despues().plano().hash().equals(otra.despues().plano().hash()), "El mismo cambio dio dos planos: " + h.es());
        for (BlockPos w : h.despues().abiertas()) {
            exige(CambioCasa.lado(ModelosCasa.Modelo.CABANA, w) == CambioCasa.Lado.IZQUIERDA, "Abrió una ventana fuera de la pared pedida: " + w);
        }
        exige(!CambioCasa.aplicar(CatalogoPlanos.Borrador.de(ModelosCasa.Modelo.LARGA, Plano.Madera.ROBLE), CambioCasa.leer("techo girado")).ok(),
                "Aceptó un techo que no se sostiene");
    }

    private static void crear(Arena a) {
        String r = Voluntad.construir(a.v, a.pide, "hazme una cabaña de abeto aquí", true);
        Propuestas.Propuesta p = a.propuesta(a.v);
        exige(p != null, "No guardó la propuesta: " + r);
        exige(a.obra(a.v) == null, "Creó la obra sin el sí del jugador");
        exige(p.plano().id.equals(ModelosCasa.Modelo.CABANA.id()) && p.madera() == Plano.Madera.ABETO, "Diseño o madera equivocados: " + p.resumen(true));
        exige(p.para.equals(a.pide.getUuid()), "No quedó para quien la pidió");
        exige(p.pendienteSitio() && p.vista().celdas().size() == p.plano().piezas.size() && p.vista().id.startsWith(Propuestas.PARCELA),
                "La vista que sigue al marcador no tiene el plano propuesto");
        exige(r.contains("only when they themselves say yes"), "Al modelo no se le dijo que no puede empezarla: " + r);
        String hash = p.plano().hash();
        // what a model can do with their "sí": call build-house again, and that only shows it again
        Voluntad.construir(a.v, a.pide, "sí", true);
        exige(a.obra(a.v) == null, "Un modelo la aprobó con build-house");
        exige(a.propuesta(a.v) == p && p.plano().hash().equals(hash), "Cambió la propuesta sin que se lo pidieran");
        exige(Voluntad.respuestaPropuesta(a.v, a.pide, "sí", true) != null && a.obra(a.v) == null && p.pendienteSitio(),
                "Aceptó una obra sin parcela");
        BlockPos suelo = O.south(14).down();
        exige(!Voluntad.elegirParcela(a.world, a.v, a.otro, p, suelo), "Otro jugador eligió la parcela");
        marcar(a, a.pide, p, suelo);
        exige(!p.pendienteSitio() && p.vista().centro().down().equals(suelo) && p.vista().id.startsWith(Propuestas.PREFIJO),
                "No fijó el fantasma en el bloque elegido");
        String dicho = Voluntad.dicho(a.v, "OBRA", true);
        exige(dicho != null && dicho.contains("piezas exactas") && dicho.contains("de base unos"), "No separó piezas exactas y material estimado: " + dicho);
    }

    private static void marcar(Arena a, ServerPlayerEntity player, Propuestas.Propuesta p, BlockPos suelo) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!MarcadorParcela.coincide(stack, a.world, player, p.id)) {
                continue;
            }
            player.getInventory().selectedSlot = slot;
            BlockHitResult hit = new BlockHitResult(Vec3d.ofBottomCenter(suelo.up()), Direction.UP, suelo, false);
            exige(stack.getItem().useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit)).isAccepted(), "El marcador no fijó la parcela");
            exige(stack.isEmpty(), "El marcador sigue en el inventario");
            return;
        }
        throw new IllegalStateException("El jugador no recibió el marcador");
    }

    private static void editar(Arena a) {
        Propuestas.Propuesta p = a.propuesta(a.v);
        exige(p != null, "No había propuesta que cambiar");
        String h0 = p.plano().hash();
        int rev0 = p.plano().revision;
        int ver0 = p.version();
        String r = Voluntad.editarCasa(a.v, a.pide, "ponle techo alto y dos ventanas a la izquierda", true);
        exige(p.borrador().cubierta() == ModelosCasa.Cubierta.ALTO && p.borrador().abiertas().size() == 2, "No aplicó techo y ventanas: " + r);
        exige(!p.plano().hash().equals(h0) && p.plano().revision == rev0 + 2 && p.version() > ver0, "No dio una revisión nueva");
        for (BlockPos w : p.borrador().abiertas()) {
            exige(CambioCasa.lado(p.borrador().modelo(), w) == CambioCasa.Lado.IZQUIERDA, "Abrió una ventana fuera de la izquierda: " + w);
        }
        String dicho = Voluntad.dicho(a.v, "OBRA", true);
        exige(dicho != null && dicho.contains("paneles de vidrio más"), "No dijo la diferencia exacta: " + dicho);
        String h1 = p.plano().hash();
        Voluntad.editarCasa(a.v, a.pide, "mejor una casa larga con techo girado", true);
        exige(p.plano().hash().equals(h1), "Un cambio rechazado cambió la propuesta");
        Voluntad.editarCasa(a.v, a.otro, "techo a dos aguas", true);
        exige(p.plano().hash().equals(h1), "Otro jugador cambió la propuesta");
    }

    private static void aprobar(Arena a) {
        Propuestas.Propuesta p = a.propuesta(a.v);
        exige(p != null, "No había propuesta que aprobar");
        Plano plano = p.plano();
        BlockPos origen = p.origen();
        Direction frente = p.frente();
        exige(Voluntad.respuestaPropuesta(a.v, a.otro, "sí", true) == null && a.obra(a.v) == null, "La aprobó otro jugador");
        exige(Voluntad.respuestaPropuesta(a.v, a.pide, "sí, pero con techo normal", true) == null && a.obra(a.v) == null, "Un sí con cambios la aprobó");
        exige(Voluntad.respuestaPropuesta(a.v, a.pide, "¿sí?", true) == null && a.obra(a.v) == null, "Una pregunta la aprobó");
        String line = Voluntad.respuestaPropuesta(a.v, a.pide, "¡Sí, dale!", true);
        Obra o = a.obra(a.v);
        exige(line != null && o != null, "El sí del jugador no la empezó: " + line);
        exige(o.plano().hash().equals(plano.hash()) && o.plano().revision == plano.revision && o.plano().id.equals(plano.id), "La obra no tiene el plano aprobado");
        exige(o.origen.equals(origen) && o.frente == frente, "La obra no quedó donde se mostró");
        exige(a.propuesta(a.v) == null, "La propuesta siguió después de aprobarla");
        exige(Voluntad.of(a.v).modo() == Voluntad.Modo.OBRA, "No se puso a construir: " + Voluntad.of(a.v).modo());
        Voluntad.editarCasa(a.v, a.pide, "techo girado", true);
        exige(o.plano().hash().equals(plano.hash()), "Cambió el plano de una obra empezada");
    }

    private static void cancelar(Arena a) {
        Voluntad.construir(a.v2, a.tercero, "una casa larga aquí", true);
        Propuestas.Propuesta p = a.propuesta(a.v2);
        exige(p != null && p.plano().id.equals(ModelosCasa.Modelo.LARGA.id()), "No propuso la casa larga");
        exige(Voluntad.respuestaPropuesta(a.v2, a.tercero, "no, gracias", true) != null && a.propuesta(a.v2) == null && a.obra(a.v2) == null,
                "El no del jugador no la descartó");
        Voluntad.construir(a.v2, a.tercero, "hazme una cabaña aquí", true);
        exige(a.propuesta(a.v2) != null, "No propuso otra vez");
        Voluntad.cancelarObra(a.v2, a.tercero, true);
        exige(a.propuesta(a.v2) == null && a.obra(a.v2) == null, "cancel-house no la descartó");
        Voluntad.construir(a.v2, a.tercero, "hazme una cabaña aquí", true);
        Propuestas.Propuesta tarde = a.propuesta(a.v2);
        exige(tarde != null, "No propuso la tercera vez");
        tarde.mostrada(a.world.getTime() - Propuestas.RESPUESTA - 1);
        exige(Voluntad.respuestaPropuesta(a.v2, a.tercero, "sí", true) == null && a.obra(a.v2) == null, "Un sí fuera de tiempo la aprobó");
    }

    private static CatalogoPlanos.Borrador borrador() {
        CambioCasa.Hecho h = CambioCasa.aplicar(CatalogoPlanos.Borrador.de(ModelosCasa.Modelo.LARGA, Plano.Madera.ABEDUL),
                CambioCasa.leer("techo alto y dos ventanas a la derecha"));
        exige(h.ok(), "El borrador de prueba no se pudo hacer: " + h.es());
        return h.despues();
    }

    private static void balcon(Arena a) {
        CatalogoPlanos.Borrador base = CatalogoPlanos.Borrador.de(ModelosCasa.Modelo.DOS_PISOS, Plano.Madera.ABETO);
        for (CambioCasa.Lado lado : CambioCasa.Lado.values()) {
            CatalogoPlanos.Resultado cambio = CatalogoPlanos.balcon(base, lado);
            exige(cambio.ok(), "No generó el balcón " + lado + ": " + cambio.porQue());
            for (Direction frente : Direction.Type.HORIZONTAL) {
                var rev = ModelosCasa.revisar(cambio.plano(), frente);
                exige(rev.ok(), "Balcón " + lado + " hacia " + frente + ": " + rev.resumen());
            }
        }
        Voluntad.cancelarObra(a.v2, a.tercero, true);
        Voluntad.construir(a.v2, a.tercero, "hazme una casa de dos pisos", true);
        Propuestas.Propuesta p = a.propuesta(a.v2);
        exige(p != null, "No propuso dos pisos");
        BlockPos suelo = O.north(18).down();
        marcar(a, a.tercero, p, suelo);
        String antes = p.plano().hash();
        Voluntad.editarCasa(a.v2, a.tercero, "ponle un balcón a la derecha", true);
        exige(p.borrador().balcon() == CambioCasa.Lado.DERECHA && !antes.equals(p.plano().hash())
                        && p.vista().centro().down().equals(suelo), "No actualizó el plano sobre la parcela");
        OpenAIChatAI chat = new OpenAIChatAI() {
            @Override
            protected Cerebros.Reply askMinds(List<Cerebros.Mind> minds, Function<Cerebros.Mind, String> system,
                                             List<Pair<String, String>> history, String msg, String playerName, String villagerName,
                                             UUID villager, long deadline, String image) {
                return new Cerebros.Reply("prueba", """
                        {"message":"Ya está construida.","optionalCommand":"edit-house","argument":"mueve el balcón a la izquierda"}
                        """, 0, false);
            }
        };
        String respuesta = chat.answer(a.tercero, a.v2, "Sorpréndeme con otro detalle, Lia").orElse("");
        exige(p.borrador().balcon() == CambioCasa.Lado.IZQUIERDA && !p.pendienteSitio() && p.vista().centro().down().equals(suelo),
                "La edición elegida por el modelo no se aplicó en la parcela: " + respuesta);
        exige(a.obra(a.v2) == null && !respuesta.contains("Ya está construida."), "El modelo afirmó que ya había construido: " + respuesta);
        var copia = Propuestas.leer(Propuestas.get(a.world).writeNbt(new NbtCompound())).de(a.v2.getUuid());
        exige(copia != null && copia.borrador().balcon() == CambioCasa.Lado.IZQUIERDA && copia.plano().hash().equals(p.plano().hash()),
                "Perdió el balcón al guardar el borrador");
        Voluntad.respuestaPropuesta(a.v2, a.tercero, "sí", true);
        Obra o = a.obra(a.v2);
        exige(o != null && o.plano().hash().equals(p.plano().hash()), "No empezó con el plano del balcón aprobado");
    }

    private static void guardado() {
        UUID constructor = UUID.fromString("c03b0000-0000-4000-8000-000000000001");
        UUID para = UUID.fromString("c03b0000-0000-4000-8000-000000000002");
        CatalogoPlanos.Borrador b = borrador();
        Propuestas ps = Propuestas.leer(new NbtCompound());
        Propuestas.Propuesta p = new Propuestas.Propuesta("prueba01", constructor, "Constructor", para, "Jugador", b, new BlockPos(1, 64, 2), Direction.EAST, 100L);
        ps.poner(p);
        NbtCompound n = ps.writeNbt(new NbtCompound());
        Propuestas back = Propuestas.leer(n.copy());
        Propuestas.Propuesta r = back.de(constructor);
        exige(r != null && r.plano().hash().equals(p.plano().hash()) && r.borrador().equals(b), "Perdió el plano o el borrador al guardar");
        exige(r.origen().equals(p.origen()) && r.frente() == Direction.EAST && r.para.equals(para) && r.creada == 100L, "Perdió sitio, dueño o fecha");
        NbtCompound malo = n.copy();
        malo.getList("propuestas", NbtElement.COMPOUND_TYPE).getCompound(0).putInt("revision", 99);
        exige(Propuestas.leer(malo).de(constructor) == null, "Leyó una propuesta cuyo borrador ya no da el plano guardado");
        exige(back.caducar(100L + Propuestas.CADUCA + 1).size() == 1 && back.de(constructor) == null, "No caducó al día sin respuesta");
    }

    private static void preparar(ServerWorld world) {
        Propuestas ps = Propuestas.get(world);
        exige(ps.de(REINICIO) == null, "La prueba requiere un primer proceso sin propuesta anterior");
        ps.poner(new Propuestas.Propuesta("reinicio", REINICIO, "Constructor de prueba", JUGADOR, "Jugador de prueba", borrador(), ORIGEN_REINICIO,
                Direction.SOUTH, world.getTime()));
        CatalogoPlanos.Resultado balcon = CatalogoPlanos.balcon(CatalogoPlanos.Borrador.de(ModelosCasa.Modelo.DOS_PISOS, Plano.Madera.ABETO),
                CambioCasa.Lado.FRENTE);
        exige(balcon.ok(), "No generó el balcón de reinicio");
        Propuestas.Propuesta pendiente = new Propuestas.Propuesta("reinicio-parcela", SIN_PARCELA, "Constructora de prueba", JUGADOR,
                "Jugador de prueba", balcon.borrador(), BlockPos.ORIGIN, Direction.NORTH, world.getTime());
        pendiente.pedirSitio(balcon.borrador(), world.getTime());
        ps.poner(pendiente);
        exige(ps.de(REINICIO) != null, "No guardó la propuesta para reiniciar");
    }

    private static void reinicio(ServerWorld world) {
        Propuestas ps = Propuestas.get(world);
        Propuestas.Propuesta p = ps.de(REINICIO);
        exige(p != null, "No leyó la propuesta del primer proceso");
        CatalogoPlanos.Borrador b = borrador();
        exige(p.plano().hash().equals(b.plano().hash()) && p.borrador().equals(b), "Perdió el plano o el borrador al reiniciar");
        exige(p.origen().equals(ORIGEN_REINICIO) && p.frente() == Direction.SOUTH && p.para.equals(JUGADOR), "Perdió sitio o dueño al reiniciar");
        exige(CambioCasa.aplicar(p.borrador(), CambioCasa.leer("techo a dos aguas")).ok(), "Tras reiniciar ya no se puede cambiar");
        exige(ObrasMundo.get(world).de(REINICIO) == null, "Una propuesta se volvió obra al reiniciar");
        Propuestas.Propuesta pendiente = ps.de(SIN_PARCELA);
        exige(pendiente != null && pendiente.pendienteSitio() && pendiente.vista().id.startsWith(Propuestas.PARCELA)
                && pendiente.borrador().balcon() == CambioCasa.Lado.FRENTE && ObrasMundo.get(world).de(SIN_PARCELA) == null,
                "Perdió el balcón o la espera de parcela al reiniciar");
        ps.quitar(pendiente);
        ps.quitar(p);
    }
}
