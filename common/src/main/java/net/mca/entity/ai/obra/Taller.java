package net.mca.entity.ai.obra;

import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.crafting.Ejecutor;
import net.mca.entity.ai.crafting.Reserva;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.world.ServerWorld;
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
public final class Taller {
    static final Predicate<ItemStack> TABLAS = s -> s.isIn(ItemTags.PLANKS);
    static final Predicate<ItemStack> TRONCOS = s -> s.isIn(ItemTags.LOGS) && !s.isOf(Items.CRIMSON_STEM) && !s.isOf(Items.WARPED_STEM);
    static final Predicate<ItemStack> PALOS = s -> s.isOf(Items.STICK);
    static final Predicate<ItemStack> PIEDRA = s -> s.isIn(ItemTags.STONE_TOOL_MATERIALS);
    static final Predicate<ItemStack> CARBON = s -> s.isOf(Items.COAL) || s.isOf(Items.CHARCOAL);
    static final Predicate<ItemStack> ARENA = s -> s.isOf(Items.SAND) || s.isOf(Items.RED_SAND);
    static final Predicate<ItemStack> VIDRIO = s -> s.isOf(Items.GLASS);

    /** What each recipe is: by hand or at a table. */
    public enum Receta {
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

    static boolean puede(VillagerEntityMCA villager, Receta r, int reservaTroncos, Plano.Madera madera) {
        SimpleInventory inv = villager.getInventory();
        return switch (r) {
            case TABLAS -> contar(inv, s -> s.isOf(madera.log.asItem())) > reservaTroncos;
            case ESCALERAS, PUERTA -> contar(inv, s -> s.isOf(madera.planks.asItem())) >= 6;
            default -> puede(villager, r, reservaTroncos);
        };
    }

    /**
     * One craft of {@code r}: true when it made something. {@code pref} is the wood it would rather use. The server's own
     * recipe does it, with the bag's own things on the grid ({@link Ejecutor}); the logs the house still needs are kept.
     */
    public static boolean hacer(VillagerEntityMCA villager, Receta r, Plano.Madera pref, int reservaTroncos) {
        if (!puede(villager, r, reservaTroncos, pref) || !(villager.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        SimpleInventory inv = villager.getInventory();
        Reserva reserva = Reserva.normal().guarda(s -> s.isOf(pref.log.asItem()), reservaTroncos);
        Ejecutor.Resultado hecho = switch (r) {
            case TABLAS -> {
                // the log of the house's own wood first: its walls and roof should be one colour
                int slot = -1;
                for (int i = 0; i < inv.size(); i++) {
                    ItemStack s = inv.getStack(i);
                    if (!s.isEmpty() && s.isOf(pref.log.asItem())) {
                        slot = i;
                    }
                }
                yield slot < 0 ? null : Ejecutor.desde(world, villager, slot, s -> s.isOf(pref.planks.asItem()), reserva, true);
            }
            case PALOS -> Ejecutor.hacer(world, villager, Items.STICK, 4, false, reserva, true);
            case MESA -> Ejecutor.hacer(world, villager, Items.CRAFTING_TABLE, 1, false, reserva, true);
            case ANTORCHAS -> Ejecutor.hacer(world, villager, Items.TORCH, 4, false, reserva, true);
            case ESCALERAS, PUERTA -> {
                Item planks = contar(inv, s -> s.isOf(pref.planks.asItem())) >= 6 ? pref.planks.asItem() : null;
                if (planks == null) {
                    yield null;
                }
                boolean escalera = r == Receta.ESCALERAS;
                Item made = variante(planks, escalera ? "_stairs" : "_door", escalera ? pref.stairs.asItem() : pref.door.asItem());
                yield Ejecutor.hacer(world, villager, made, escalera ? 4 : 3, true, reserva, true);
            }
            case COFRE -> Ejecutor.hacer(world, villager, Items.CHEST, 1, true, reserva, true);
            case HORNO -> Ejecutor.hacer(world, villager, Items.FURNACE, 1, true, reserva, true);
            case PANELES -> Ejecutor.hacer(world, villager, Items.GLASS_PANE, 16, true, reserva, true);
            case PICO -> Ejecutor.hacer(world, villager, Items.WOODEN_PICKAXE, 1, true, reserva, true);
        };
        return hecho != null && hecho.ok();
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
