package net.mca.server.command;

import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.body.Progreso;
import net.mca.entity.ai.crafting.Ejecutor;
import net.mca.entity.ai.crafting.Planificador;
import net.mca.entity.ai.crafting.Reserva;
import net.minecraft.block.Blocks;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * F16 checks: crafting with the server's own recipes. One command, {@code mca-prueba recetas}, ten terminal results
 * (see {@link #NOMBRES}). Arena x 10000..10170, z 40, y 100. Every check reads the bag before and after: a craft has
 * to show in what was used up and what came out, not in what anyone said.
 */
final class PruebasRecetas {
    static final List<String> NOMBRES = List.of("recetas-heno-pan", "recetas-un-pan", "recetas-sin-mesa", "recetas-lleno",
            "recetas-restos", "recetas-ciclo", "recetas-cambio", "recetas-reserva", "recetas-progreso", "recetas-tablas");

    private PruebasRecetas() {
    }

    /** Starts the recipe checks when {@code which} names them; false otherwise. */
    static boolean empezar(ServerWorld world, String which) {
        if (!"recetas".equals(which)) {
            return false;
        }
        Pruebas.start(new Recetas(world));
        return true;
    }

    private static final class Recetas extends Pruebas.Scenario {
        private static final BlockPos O = new BlockPos(10000, 100, 40);
        private final List<VillagerEntityMCA> villagers = new ArrayList<>();

        Recetas(ServerWorld world) {
            super("recetas", world, 400);
            for (int i = 0; i <= 12; i++) {
                Pruebas.load(world, O.east(16 * i), 8);
            }
            Pruebas.fill(world, O.add(-4, -1, -6), O.add(170, -1, 6), Blocks.STONE.getDefaultState());
            Pruebas.fill(world, O.add(-4, 0, -6), O.add(170, 4, 6), Blocks.AIR.getDefaultState());
        }

        /** A villager of its own for each check, sixteen blocks apart: no table of one is within reach of another. */
        private VillagerEntityMCA aldeano(int i, String name) {
            VillagerEntityMCA v = Pruebas.villager(world, O.east(16 * i), Direction.EAST, name);
            villagers.add(v);
            return v;
        }

        private BlockPos mesa(int i, int dx) {
            BlockPos p = O.east(16 * i + dx);
            world.setBlockState(p, Blocks.CRAFTING_TABLE.getDefaultState(), 2);
            return p;
        }

        @Override
        @Nullable
        Boolean step() {
            if (ticks < 20) {
                return null;
            }
            henoPan();
            unPan();
            sinMesa();
            lleno();
            restos();
            ciclo();
            cambio();
            reserva();
            progreso();
            tablas();
            return Boolean.TRUE;
        }

        /** One hay bale and a table: nine wheat, then three breads, nothing left over. */
        private void henoPan() {
            VillagerEntityMCA v = aldeano(0, "Receta Heno");
            mesa(0, 1);
            v.getInventory().addStack(new ItemStack(Items.HAY_BLOCK));
            boolean cerca = Ejecutor.mesaCerca(world, v);
            Ejecutor.Resultado r = Ejecutor.hacer(world, v, Items.BREAD, 3, cerca, Reserva.normal());
            SimpleInventory inv = v.getInventory();
            int pan = n(inv, Items.BREAD);
            int trigo = n(inv, Items.WHEAT);
            int heno = n(inv, Items.HAY_BLOCK);
            Pruebas.check("recetas-heno-pan", cerca && r.ok() && pan == 3 && trigo == 0 && heno == 0,
                    "mesa al alcance " + cerca + "; " + r.tipo() + " " + r.motivo() + "; pan " + pan + ", trigo " + trigo + ", heno " + heno);
        }

        /** One bread asked for: one conversion, six wheat left. */
        private void unPan() {
            VillagerEntityMCA v = aldeano(1, "Receta Uno");
            mesa(1, 1);
            v.getInventory().addStack(new ItemStack(Items.HAY_BLOCK));
            Ejecutor.Resultado r = Ejecutor.hacer(world, v, Items.BREAD, 1, Ejecutor.mesaCerca(world, v), Reserva.normal());
            SimpleInventory inv = v.getInventory();
            int pan = n(inv, Items.BREAD);
            int trigo = n(inv, Items.WHEAT);
            int heno = n(inv, Items.HAY_BLOCK);
            Pruebas.check("recetas-un-pan", r.ok() && pan == 1 && trigo == 6 && heno == 0,
                    r.tipo() + " " + r.motivo() + "; pan " + pan + ", trigo " + trigo + ", heno " + heno);
        }

        /** No table, a table out of reach, a table behind a wall: bread is not made and the hay is not touched. */
        private void sinMesa() {
            VillagerEntityMCA v = aldeano(2, "Receta Sin Mesa");
            v.getInventory().addStack(new ItemStack(Items.HAY_BLOCK));
            boolean ninguna = Ejecutor.mesaCerca(world, v);
            Ejecutor.Resultado r = Ejecutor.hacer(world, v, Items.BREAD, 1, ninguna, Reserva.normal());
            mesa(2, 8);
            boolean lejos = Ejecutor.mesaCerca(world, v);
            VillagerEntityMCA w = aldeano(3, "Receta Muro");
            BlockPos at = O.east(16 * 3);
            Pruebas.fill(world, at.add(1, 0, -2), at.add(1, 3, 2), Blocks.STONE.getDefaultState());
            mesa(3, 2);
            boolean muro = Ejecutor.mesaCerca(world, w);
            SimpleInventory inv = v.getInventory();
            int heno = n(inv, Items.HAY_BLOCK);
            int trigo = n(inv, Items.WHEAT);
            Pruebas.check("recetas-sin-mesa", !ninguna && !lejos && !muro && r.tipo() == Ejecutor.Tipo.SIN_MESA && heno == 1 && trigo == 0,
                    "sin mesa: " + r.tipo() + " " + r.motivo() + "; mesa a 8 bloques al alcance " + lejos + "; detrás de un muro al alcance " + muro
                            + "; heno " + heno + ", trigo " + trigo);
        }

        /** A full bag: two hay bales leave no room for the wheat (nothing used); one bale frees its own slot. */
        private void lleno() {
            VillagerEntityMCA v = aldeano(4, "Receta Llena");
            SimpleInventory inv = v.getInventory();
            inv.setStack(0, new ItemStack(Items.HAY_BLOCK, 2));
            for (int i = 1; i < inv.size(); i++) {
                inv.setStack(i, new ItemStack(Items.DIRT, 64));
            }
            int tierra = n(inv, Items.DIRT);
            Ejecutor.Resultado r1 = Ejecutor.hacer(world, v, Items.WHEAT, 9, false, Reserva.normal());
            int heno1 = n(inv, Items.HAY_BLOCK);
            int trigo1 = n(inv, Items.WHEAT);
            inv.setStack(0, new ItemStack(Items.HAY_BLOCK, 1));
            Ejecutor.Resultado r2 = Ejecutor.hacer(world, v, Items.WHEAT, 9, false, Reserva.normal());
            int heno2 = n(inv, Items.HAY_BLOCK);
            int trigo2 = n(inv, Items.WHEAT);
            int tierra2 = n(inv, Items.DIRT);
            Pruebas.check("recetas-lleno", r1.tipo() == Ejecutor.Tipo.LLENO && heno1 == 2 && trigo1 == 0
                            && r2.ok() && heno2 == 0 && trigo2 == 9 && tierra2 == tierra,
                    "con 2 balas: " + r1.tipo() + " (heno " + heno1 + ", trigo " + trigo1 + "); con 1: " + r2.tipo()
                            + " (heno " + heno2 + ", trigo " + trigo2 + "); tierra " + tierra + " -> " + tierra2);
        }

        /** A cake: the three milk buckets come back empty. */
        private void restos() {
            VillagerEntityMCA v = aldeano(5, "Receta Pastel");
            mesa(5, 1);
            SimpleInventory inv = v.getInventory();
            for (int i = 0; i < 3; i++) {
                inv.addStack(new ItemStack(Items.MILK_BUCKET));
            }
            inv.addStack(new ItemStack(Items.SUGAR, 2));
            inv.addStack(new ItemStack(Items.EGG));
            inv.addStack(new ItemStack(Items.WHEAT, 3));
            Ejecutor.Resultado r = Ejecutor.hacer(world, v, Items.CAKE, 1, Ejecutor.mesaCerca(world, v), Reserva.normal());
            int pastel = n(inv, Items.CAKE);
            int cubos = n(inv, Items.BUCKET);
            int leche = n(inv, Items.MILK_BUCKET);
            Pruebas.check("recetas-restos", r.ok() && pastel == 1 && cubos == 3 && leche == 0 && n(inv, Items.WHEAT) == 0,
                    r.tipo() + " " + r.motivo() + "; pastel " + pastel + ", cubos vacíos " + cubos + ", con leche " + leche);
        }

        /** Wheat out of wheat (through hay) gives nothing; hay out of wheat does; an empty bag stops quickly. */
        private void ciclo() {
            VillagerEntityMCA v = aldeano(6, "Receta Ciclo");
            SimpleInventory inv = v.getInventory();
            inv.addStack(new ItemStack(Items.WHEAT, 9));
            Planificador.Plan trigo = Planificador.planear(world, inv, Items.WHEAT, 9, Reserva.normal());
            Planificador.Plan heno = Planificador.planear(world, inv, Items.HAY_BLOCK, 1, Reserva.normal());
            inv.clear();
            Planificador.Plan vacio = Planificador.planear(world, inv, Items.WHEAT, 1, Reserva.normal());
            Planificador.Plan pan = Planificador.planear(world, inv, Items.BREAD, 1, Reserva.normal());
            boolean ok = !trigo.posible() && heno.posible() && heno.pasos().size() == 1 && !vacio.posible()
                    && vacio.expansiones() < 100 && !pan.posible() && pan.falta() == Items.WHEAT;
            Pruebas.check("recetas-ciclo", ok, "trigo de trigo " + trigo.posible() + " (" + trigo.expansiones() + " pasos probados); heno de trigo "
                    + heno.posible() + " en " + heno.pasos().size() + " paso; trigo sin nada " + vacio.posible() + " (" + vacio.expansiones()
                    + " pasos probados); pan sin nada le falta " + (pan.falta() == null ? "nada" : pan.falta().getName().getString()));
        }

        /** The bag changes between the plan and the work: nothing is made from the hay that left; planned again, it works. */
        private void cambio() {
            VillagerEntityMCA v = aldeano(7, "Receta Cambio");
            mesa(7, 1);
            SimpleInventory inv = v.getInventory();
            inv.setStack(0, new ItemStack(Items.HAY_BLOCK));
            boolean cerca = Ejecutor.mesaCerca(world, v);
            Planificador.Plan plan = Planificador.planear(world, inv, Items.BREAD, 3, Reserva.normal());
            inv.setStack(0, ItemStack.EMPTY); // the player takes the hay back
            Ejecutor.Resultado viejo = Ejecutor.seguir(world, v, plan, cerca, Reserva.normal(), Items.BREAD);
            int trigo = n(inv, Items.WHEAT);
            int pan1 = n(inv, Items.BREAD);
            inv.setStack(0, new ItemStack(Items.HAY_BLOCK));
            Ejecutor.Resultado nuevo = Ejecutor.hacer(world, v, Items.BREAD, 3, cerca, Reserva.normal());
            int pan2 = n(inv, Items.BREAD);
            Pruebas.check("recetas-cambio", plan.posible() && plan.mesa() && !viejo.ok() && trigo == 0 && pan1 == 0 && nuevo.ok() && pan2 == 3,
                    "plan " + plan.pasos().size() + " pasos, mesa " + plan.mesa() + "; sin el heno: " + viejo.tipo() + " " + viejo.motivo()
                            + " (trigo " + trigo + ", pan " + pan1 + "); con el heno de vuelta: " + nuevo.tipo() + ", pan " + pan2);
        }

        /** Its own named wheat is never used; hay kept back for another job is not used either. */
        private void reserva() {
            VillagerEntityMCA v = aldeano(8, "Receta Reserva");
            mesa(8, 1);
            SimpleInventory inv = v.getInventory();
            ItemStack suyo = new ItemStack(Items.WHEAT, 3);
            suyo.setCustomName(Text.literal("Trigo de la abuela"));
            inv.addStack(suyo);
            inv.addStack(new ItemStack(Items.HAY_BLOCK));
            boolean cerca = Ejecutor.mesaCerca(world, v);
            Ejecutor.Resultado r1 = Ejecutor.hacer(world, v, Items.BREAD, 1, cerca, Reserva.normal());
            int nombrado = 0;
            int trigo = 0;
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.getStack(i);
                if (s.isOf(Items.WHEAT)) {
                    if (s.hasCustomName()) {
                        nombrado += s.getCount();
                    } else {
                        trigo += s.getCount();
                    }
                }
            }
            VillagerEntityMCA w = aldeano(9, "Receta Guarda");
            mesa(9, 1);
            w.getInventory().addStack(new ItemStack(Items.HAY_BLOCK));
            Ejecutor.Resultado r2 = Ejecutor.hacer(world, w, Items.BREAD, 1, Ejecutor.mesaCerca(world, w),
                    Reserva.normal().guarda(s -> s.isOf(Items.HAY_BLOCK), 1));
            int heno = n(w.getInventory(), Items.HAY_BLOCK);
            Pruebas.check("recetas-reserva", r1.ok() && n(inv, Items.BREAD) == 1 && nombrado == 3 && trigo == 6
                            && r2.tipo() == Ejecutor.Tipo.FALTA && heno == 1,
                    "con trigo propio: " + r1.tipo() + ", pan " + n(inv, Items.BREAD) + ", trigo con nombre " + nombrado + ", trigo suelto " + trigo
                            + "; heno guardado: " + r2.tipo() + " " + r2.motivo() + ", heno " + heno);
        }

        /** Progreso's own paths: bread from hay right now, and "hazme pan" accepted with a hay bale and no wheat. */
        private void progreso() {
            VillagerEntityMCA v = aldeano(10, "Receta Progreso");
            SimpleInventory inv = v.getInventory();
            inv.addStack(new ItemStack(Items.HAY_BLOCK));
            inv.addStack(new ItemStack(Items.CRAFTING_TABLE));
            Progreso p = new Progreso();
            boolean pan = p.pan(world, v);
            int panes = n(inv, Items.BREAD);
            int trigo = n(inv, Items.WHEAT);
            VillagerEntityMCA w = aldeano(11, "Receta Encargo");
            w.getInventory().addStack(new ItemStack(Items.HAY_BLOCK));
            String[] conHeno = new Progreso().falta(world, w, Items.BREAD, 3);
            w.getInventory().clear();
            String[] sinNada = new Progreso().falta(world, w, Items.BREAD, 3);
            Pruebas.check("recetas-progreso", pan && panes == 1 && trigo == 6 && conHeno == null && sinNada != null,
                    "pan ahora " + pan + " (pan " + panes + ", trigo " + trigo + "); hazme pan con heno: "
                            + (conHeno == null ? "puede" : conHeno[0]) + "; sin nada: " + (sinNada == null ? "puede" : sinNada[0]));
        }

        /** Planks are what the server's recipe for that log gives (birch, crimson), and two of them make four sticks. */
        private void tablas() {
            VillagerEntityMCA v = aldeano(12, "Receta Tablas");
            SimpleInventory inv = v.getInventory();
            inv.setStack(0, new ItemStack(Items.BIRCH_LOG));
            inv.setStack(1, new ItemStack(Items.CRIMSON_STEM));
            Ejecutor.Resultado abedul = Ejecutor.desde(world, v, 0, s -> s.isIn(net.minecraft.registry.tag.ItemTags.PLANKS), Reserva.normal());
            Ejecutor.Resultado carmesi = Ejecutor.desde(world, v, 1, s -> s.isIn(net.minecraft.registry.tag.ItemTags.PLANKS), Reserva.normal());
            int birch = n(inv, Items.BIRCH_PLANKS);
            int crimson = n(inv, Items.CRIMSON_PLANKS);
            Ejecutor.Resultado palos = Ejecutor.hacer(world, v, Items.STICK, 4, false, Reserva.normal());
            int sticks = n(inv, Items.STICK);
            int tablas = n(inv, Items.BIRCH_PLANKS) + n(inv, Items.CRIMSON_PLANKS);
            Pruebas.check("recetas-tablas", abedul.ok() && carmesi.ok() && birch == 4 && crimson == 4 && palos.ok() && sticks == 4 && tablas == 6,
                    "abedul " + abedul.tipo() + " (" + birch + "), carmesí " + carmesi.tipo() + " (" + crimson + "), palos " + palos.tipo()
                            + " (" + sticks + ", quedan " + tablas + " tablas)");
        }

        private static int n(SimpleInventory inv, Item item) {
            int c = 0;
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.getStack(i);
                if (s.isOf(item)) {
                    c += s.getCount();
                }
            }
            return c;
        }

        @Override
        String state() {
            return ticks < 20 ? "preparando" : "comprobando";
        }

        @Override
        void cleanup() {
            for (VillagerEntityMCA v : villagers) {
                v.discard();
            }
        }
    }
}
