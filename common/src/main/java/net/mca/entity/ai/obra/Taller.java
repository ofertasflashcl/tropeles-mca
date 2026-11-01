package net.mca.entity.ai.obra;

import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.body.Progreso;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The vanilla recipes a house takes, crafted from what the bag really holds and nothing else: planks from a log,
 * sticks, a crafting table and torches by hand (the 2x2 grid); stairs, a door, a chest, a furnace, glass panes and
 * a wooden pickaxe at a crafting table (the 3x3 grid). Stairs and doors come out in the wood of the planks used.
 */
final class Taller {
    static final Predicate<ItemStack> TABLAS = s -> s.isIn(ItemTags.PLANKS);
    static final Predicate<ItemStack> TRONCOS = s -> s.isIn(ItemTags.LOGS) && !s.isOf(Items.CRIMSON_STEM) && !s.isOf(Items.WARPED_STEM);
    static final Predicate<ItemStack> PALOS = s -> s.isOf(Items.STICK);
    static final Predicate<ItemStack> PIEDRA = s -> s.isIn(ItemTags.STONE_TOOL_MATERIALS);
    static final Predicate<ItemStack> CARBON = s -> s.isOf(Items.COAL) || s.isOf(Items.CHARCOAL);
    static final Predicate<ItemStack> ARENA = s -> s.isOf(Items.SAND) || s.isOf(Items.RED_SAND);
    static final Predicate<ItemStack> VIDRIO = s -> s.isOf(Items.GLASS);

    /** What each recipe is: by hand or at a table. */
    enum Receta {
        TABLAS(false), PALOS(false), MESA(false), ANTORCHAS(false),
        ESCALERAS(true), PUERTA(true), COFRE(true), HORNO(true), PANELES(true), PICO(true);

        final boolean mesa;

        Receta(boolean mesa) {
            this.mesa = mesa;
        }
    }

    private Taller() {
    }

    static int contar(SimpleInventory inv, Predicate<ItemStack> p) {
        int n = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && p.test(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Takes {@code n} of what matches out of the bag; false (and nothing taken) when there is not that much. */
    static boolean quitar(SimpleInventory inv, Predicate<ItemStack> p, int n) {
        if (contar(inv, p) < n) {
            return false;
        }
        for (int i = 0; i < inv.size() && n > 0; i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && p.test(s)) {
                int k = Math.min(n, s.getCount());
                s.decrement(k);
                n -= k;
            }
        }
        inv.markDirty();
        return true;
    }

    /** Into the bag; what does not fit falls at its feet (and is picked up again later). */
    static void dar(VillagerEntityMCA villager, ItemStack stack) {
        ItemStack left = villager.getInventory().addStack(stack);
        if (!left.isEmpty()) {
            villager.dropStack(left);
        }
    }

    /** Whether it can make this now from what it carries (a table recipe also needs a table, which the caller sees to). */
    static boolean puede(VillagerEntityMCA villager, Receta r, int reservaTroncos) {
        SimpleInventory inv = villager.getInventory();
        return switch (r) {
            case TABLAS -> contar(inv, TRONCOS) > reservaTroncos;
            case PALOS -> contar(inv, TABLAS) >= 2;
            case MESA -> contar(inv, TABLAS) >= 4;
            case ANTORCHAS -> contar(inv, CARBON) >= 1 && contar(inv, PALOS) >= 1;
            case ESCALERAS, PUERTA -> mismaMadera(inv, 6, null) != null;
            case COFRE -> contar(inv, TABLAS) >= 8;
            case HORNO -> contar(inv, PIEDRA) >= 8;
            case PANELES -> contar(inv, VIDRIO) >= 6;
            case PICO -> contar(inv, TABLAS) >= 3 && contar(inv, PALOS) >= 2;
        };
    }

    /** One craft of {@code r}: true when it made something. {@code pref} is the wood it would rather use. */
    static boolean hacer(VillagerEntityMCA villager, Receta r, Plano.Madera pref, int reservaTroncos) {
        if (!puede(villager, r, reservaTroncos)) {
            return false;
        }
        SimpleInventory inv = villager.getInventory();
        switch (r) {
            case TABLAS -> {
                // the log of the house's own wood first: its walls and roof should be one colour
                int slot = -1;
                for (int i = 0; i < inv.size(); i++) {
                    ItemStack s = inv.getStack(i);
                    if (!s.isEmpty() && TRONCOS.test(s) && (slot < 0 || s.isOf(pref.log.asItem()))) {
                        slot = i;
                    }
                }
                if (slot < 0) {
                    return false;
                }
                Item planks = Progreso.planksFor(inv.getStack(slot).getItem());
                inv.getStack(slot).decrement(1);
                inv.markDirty();
                dar(villager, new ItemStack(planks, 4));
            }
            case PALOS -> {
                quitar(inv, TABLAS, 2);
                dar(villager, new ItemStack(Items.STICK, 4));
            }
            case MESA -> {
                quitar(inv, TABLAS, 4);
                dar(villager, new ItemStack(Items.CRAFTING_TABLE));
            }
            case ANTORCHAS -> {
                quitar(inv, CARBON, 1);
                quitar(inv, PALOS, 1);
                dar(villager, new ItemStack(Items.TORCH, 4));
            }
            case ESCALERAS, PUERTA -> {
                Item planks = mismaMadera(inv, 6, pref.planks.asItem());
                if (planks == null) {
                    return false;
                }
                Item made = variante(planks, r == Receta.ESCALERAS ? "_stairs" : "_door",
                        r == Receta.ESCALERAS ? pref.stairs.asItem() : pref.door.asItem());
                quitar(inv, s -> s.isOf(planks), 6);
                dar(villager, new ItemStack(made, r == Receta.ESCALERAS ? 4 : 3));
            }
            case COFRE -> {
                quitar(inv, TABLAS, 8);
                dar(villager, new ItemStack(Items.CHEST));
            }
            case HORNO -> {
                quitar(inv, PIEDRA, 8);
                dar(villager, new ItemStack(Items.FURNACE));
            }
            case PANELES -> {
                quitar(inv, VIDRIO, 6);
                dar(villager, new ItemStack(Items.GLASS_PANE, 16));
            }
            case PICO -> {
                quitar(inv, TABLAS, 3);
                quitar(inv, PALOS, 2);
                dar(villager, new ItemStack(Items.WOODEN_PICKAXE));
            }
        }
        return true;
    }

    /** Planks of one wood it has at least {@code n} of ({@code pref} when it can), or null. */
    @Nullable
    static Item mismaMadera(SimpleInventory inv, int n, @Nullable Item pref) {
        Map<Item, Integer> by = new HashMap<>();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && TABLAS.test(s)) {
                by.merge(s.getItem(), s.getCount(), Integer::sum);
            }
        }
        if (pref != null && by.getOrDefault(pref, 0) >= n) {
            return pref;
        }
        Item best = null;
        int most = n - 1;
        for (Map.Entry<Item, Integer> e : by.entrySet()) {
            if (e.getValue() > most) {
                most = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    /** The stairs or door of the same wood as these planks ("spruce_planks" makes "spruce_stairs"), or {@code fallback}. */
    static Item variante(Item planks, String suffix, Item fallback) {
        Identifier id = Registries.ITEM.getId(planks);
        String path = id.getPath();
        if (!path.endsWith("_planks")) {
            return fallback;
        }
        Item made = Registries.ITEM.get(new Identifier(id.getNamespace(), path.substring(0, path.length() - "_planks".length()) + suffix));
        return made == Items.AIR ? fallback : made;
    }
}
