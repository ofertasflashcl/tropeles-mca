package net.mca.entity.ai.crafting;

import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * What a craft may not use up. Personal things never go on the grid (enchanted, or with a name of their own), and an
 * amount another job counts on stays in the bag (the logs a house keeps back, say). Only the villager's own bag is ever
 * crafted from: a hay bale standing in the world is someone's decoration, not wheat.
 */
public final class Reserva {
    private record Guardar(Predicate<ItemStack> que, int cuantos) {
    }

    private static final Reserva NORMAL = new Reserva(List.of());

    private final List<Guardar> guardar;

    private Reserva(List<Guardar> guardar) {
        this.guardar = guardar;
    }

    /** Nothing kept back but its personal things. */
    public static Reserva normal() {
        return NORMAL;
    }

    /** The same, keeping {@code cuantos} of what matches {@code que} in the bag. */
    public Reserva guarda(Predicate<ItemStack> que, int cuantos) {
        if (cuantos <= 0) {
            return this;
        }
        List<Guardar> g = new ArrayList<>(guardar);
        g.add(new Guardar(que, cuantos));
        return new Reserva(List.copyOf(g));
    }

    /** Enchanted or named: its own, never an ingredient. */
    public static boolean personal(ItemStack stack) {
        return stack.hasEnchantments() || stack.hasCustomName();
    }

    /** For every slot of the bag, how many it may use up. */
    public int[] usables(SimpleInventory inv) {
        int[] libres = new int[inv.size()];
        for (int i = 0; i < libres.length; i++) {
            ItemStack s = inv.getStack(i);
            libres[i] = s.isEmpty() || personal(s) ? 0 : s.getCount();
        }
        // what is kept back comes off the last stacks first, the way a player keeps a spare at the end of the bag
        for (Guardar g : guardar) {
            int keep = g.cuantos();
            for (int i = libres.length - 1; i >= 0 && keep > 0; i--) {
                if (libres[i] > 0 && g.que().test(inv.getStack(i))) {
                    int k = Math.min(keep, libres[i]);
                    libres[i] -= k;
                    keep -= k;
                }
            }
        }
        return libres;
    }
}
