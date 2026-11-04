package net.mca.entity.ai.crafting;

import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Works out, backwards from what is wanted, which of the server's recipes to run and how many times, from a copy of the
 * bag: a hay bale turned into nine wheat, then three breads at a table. Planning never touches the bag. A plan says
 * whether it needs a crafting table; it is checked again, step by step, when it is carried out ({@link Ejecutor}).
 * <p>
 * Cycles are cut: while it makes X it neither uses X up nor makes X again, so wheat into hay into wheat gives nothing.
 * Depth and the number of steps tried are bounded, so a missing stick never searches every item there is.
 */
public final class Planificador {
    private static final int PROFUNDIDAD = 5;
    private static final int PRESUPUESTO = 4000;
    /** Items tried for one ingredient that has many ("any planks"), the likeliest first. */
    private static final int CANDIDATOS = 8;

    /** Run {@code opcion} this many times. */
    public record Paso(Recetario.Opcion opcion, int veces) {
    }

    /** The steps in the order they run, whether one needs a table, and what was missing when it cannot be done. */
    public record Plan(List<Paso> pasos, boolean mesa, @Nullable Item falta, int expansiones) {
        public boolean posible() {
            return falta == null;
        }
    }

    private Planificador() {
    }

    /** How to make {@code cuantos} more of {@code meta} from what the bag holds now, a crafting table allowed. */
    public static Plan planear(ServerWorld world, SimpleInventory bolsa, Item meta, int cuantos, Reserva reserva) {
        return planear(world, bolsa, meta, cuantos, reserva, true);
    }

    /**
     * How to make {@code cuantos} more of {@code meta} from what the bag holds now (what it already has is not counted).
     * Without {@code mesa} only what fits the 2x2 grid of its hands is considered.
     */
    public static Plan planear(ServerWorld world, SimpleInventory bolsa, Item meta, int cuantos, Reserva reserva, boolean mesa) {
        Busqueda b = new Busqueda(Recetario.indice(world), bolsa, reserva, mesa);
        boolean ok = b.producir(meta, Math.max(1, cuantos), 0);
        if (!ok) {
            return new Plan(List.of(), false, b.falta != null ? b.falta : meta, b.expansiones);
        }
        boolean conMesa = false;
        for (Paso p : b.pasos) {
            conMesa |= p.opcion().mesa();
        }
        return new Plan(List.copyOf(b.pasos), conMesa, null, b.expansiones);
    }

    private static final class Busqueda {
        final Map<Item, List<Recetario.Opcion>> indice;
        final List<ItemStack> sim = new ArrayList<>();
        final List<Paso> pasos = new ArrayList<>();
        final Set<Item> enCurso = new HashSet<>();
        final boolean mesa;
        int expansiones;
        @Nullable
        Item falta;
        int faltaHondo = Integer.MAX_VALUE;

        Busqueda(Map<Item, List<Recetario.Opcion>> indice, SimpleInventory bolsa, Reserva reserva, boolean mesa) {
            this.indice = indice;
            this.mesa = mesa;
            int[] libres = reserva.usables(bolsa);
            for (int i = 0; i < libres.length; i++) {
                if (libres[i] > 0) {
                    sim.add(bolsa.getStack(i).copyWithCount(libres[i]));
                }
            }
        }

        boolean producir(Item item, int n, int hondo) {
            if (n <= 0) {
                return true;
            }
            List<Recetario.Opcion> opciones = indice.getOrDefault(item, List.of());
            if (hondo > PROFUNDIDAD || enCurso.contains(item) || opciones.isEmpty() || ++expansiones > PRESUPUESTO) {
                anotar(item, hondo);
                return false;
            }
            enCurso.add(item);
            try {
                for (Recetario.Opcion o : ordenar(opciones)) {
                    if (o.mesa() && !mesa) {
                        continue; // the 3x3 grid of a table, and there is none
                    }
                    List<ItemStack> antes = copiar(sim);
                    int pasosAntes = pasos.size();
                    int veces = (n + o.rinde() - 1) / o.rinde();
                    boolean ok = true;
                    for (Ingredient ing : o.ingredientes()) {
                        if (!obtener(ing, veces, hondo + 1)) {
                            ok = false;
                            break;
                        }
                    }
                    if (ok) {
                        pasos.add(new Paso(o, veces));
                        sim.add(o.salida().copyWithCount(o.rinde() * veces));
                        return true;
                    }
                    restaurar(antes, pasosAntes);
                }
                return false;
            } finally {
                enCurso.remove(item);
            }
        }

        /** {@code n} of what fits {@code ing}: what the bag has first, the rest made. */
        boolean obtener(Ingredient ing, int n, int hondo) {
            int resta = n;
            for (ItemStack s : sim) {
                if (resta == 0) {
                    break;
                }
                if (s.isEmpty() || enCurso.contains(s.getItem()) || !ing.test(s)) {
                    continue; // never what it is making right now: no wheat out of wheat
                }
                int k = Math.min(resta, s.getCount());
                s.decrement(k);
                resta -= k;
            }
            if (resta == 0) {
                return true;
            }
            for (Item cand : candidatos(ing)) {
                List<ItemStack> antes = copiar(sim);
                int pasosAntes = pasos.size();
                if (producir(cand, resta, hondo)) {
                    int need = resta;
                    for (ItemStack s : sim) {
                        if (need == 0) {
                            break;
                        }
                        if (!s.isEmpty() && s.isOf(cand) && ing.test(s)) {
                            int k = Math.min(need, s.getCount());
                            s.decrement(k);
                            need -= k;
                        }
                    }
                    if (need == 0) {
                        return true;
                    }
                }
                restaurar(antes, pasosAntes);
            }
            ItemStack[] m = ing.getMatchingStacks();
            if (m.length > 0) {
                anotar(m[0].getItem(), hondo);
            }
            return false;
        }

        /** The items an ingredient takes that the server can make, those it can make straight from the bag first. */
        List<Item> candidatos(Ingredient ing) {
            Set<Item> seen = new LinkedHashSet<>();
            for (ItemStack s : ing.getMatchingStacks()) {
                if (!s.isEmpty() && indice.containsKey(s.getItem()) && !enCurso.contains(s.getItem())) {
                    seen.add(s.getItem());
                }
            }
            List<Item> directos = new ArrayList<>();
            List<Item> otros = new ArrayList<>();
            for (Item item : seen) {
                boolean directo = false;
                for (Recetario.Opcion o : indice.get(item)) {
                    if (directo(o)) {
                        directo = true;
                        break;
                    }
                }
                (directo ? directos : otros).add(item);
            }
            directos.addAll(otros);
            return directos.size() > CANDIDATOS ? directos.subList(0, CANDIDATOS) : directos;
        }

        List<Recetario.Opcion> ordenar(List<Recetario.Opcion> opciones) {
            List<Recetario.Opcion> directas = new ArrayList<>();
            List<Recetario.Opcion> otras = new ArrayList<>();
            for (Recetario.Opcion o : opciones) {
                (directo(o) ? directas : otras).add(o);
            }
            directas.addAll(otras);
            return directas;
        }

        /** Every ingredient of {@code o} is in the bag already (one of each, at least). */
        boolean directo(Recetario.Opcion o) {
            for (Ingredient ing : o.ingredientes()) {
                boolean hay = false;
                for (ItemStack s : sim) {
                    if (!s.isEmpty() && !enCurso.contains(s.getItem()) && ing.test(s)) {
                        hay = true;
                        break;
                    }
                }
                if (!hay) {
                    return false;
                }
            }
            return true;
        }

        /** What it lacks, the thing closest to the goal: "wheat" for bread, not what wheat comes from. */
        void anotar(Item item, int hondo) {
            if (hondo > 0 && hondo < faltaHondo) {
                falta = item;
                faltaHondo = hondo;
            }
        }

        void restaurar(List<ItemStack> antes, int pasosAntes) {
            sim.clear();
            sim.addAll(antes);
            while (pasos.size() > pasosAntes) {
                pasos.remove(pasos.size() - 1);
            }
        }

        static List<ItemStack> copiar(List<ItemStack> list) {
            List<ItemStack> out = new ArrayList<>(list.size());
            for (ItemStack s : list) {
                out.add(s.copy());
            }
            return out;
        }
    }
}
