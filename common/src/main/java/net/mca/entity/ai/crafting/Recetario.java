package net.mca.entity.ai.crafting;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.ShapelessRecipe;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The crafting recipes the server has right now, datapacks included, by what they make. Nothing here is written by
 * hand or by a language model: a recipe a datapack removes is gone, one it adds is there. Shaped and shapeless crafting
 * only; the special recipes (maps, fireworks, banners, repairs) and the crafting kinds of other mods need an adapter of
 * their own and are left out.
 */
public final class Recetario {
    /** One way to make something on a crafting grid, exactly as the server has it. */
    public record Opcion(CraftingRecipe receta, Identifier id, ItemStack salida, List<Ingredient> casillas,
                         int ancho, int alto, boolean conForma, boolean mesa) {
        public Item item() {
            return salida.getItem();
        }

        /** How many one craft gives. */
        public int rinde() {
            return Math.max(1, salida.getCount());
        }

        /** The cells that hold something (a pattern's blanks left out), one item each per craft. */
        public List<Ingredient> ingredientes() {
            List<Ingredient> out = new ArrayList<>();
            for (Ingredient ing : casillas) {
                if (!ing.isEmpty()) {
                    out.add(ing);
                }
            }
            return out;
        }
    }

    @Nullable
    private static Map<Item, List<Opcion>> indice;
    @Nullable
    private static RecipeManager de;
    /** Two recipes of the index as the server had them: a datapack reload makes new recipe objects, so they stop matching. */
    @Nullable
    private static Recipe<?> testigoA;
    @Nullable
    private static Recipe<?> testigoB;

    private Recetario() {
    }

    /** Every recipe that makes something, by what it makes; read again whenever the server's recipes changed. */
    public static synchronized Map<Item, List<Opcion>> indice(ServerWorld world) {
        RecipeManager manager = world.getRecipeManager();
        if (indice != null && de == manager && vigente(manager, testigoA) && vigente(manager, testigoB)) {
            return indice;
        }
        DynamicRegistryManager reg = world.getRegistryManager();
        Map<Item, List<Opcion>> m = new HashMap<>();
        Recipe<?> a = null;
        Recipe<?> b = null;
        for (CraftingRecipe r : manager.listAllOfType(RecipeType.CRAFTING)) {
            Opcion o = opcion(r, reg);
            if (o == null) {
                continue;
            }
            m.computeIfAbsent(o.item(), k -> new ArrayList<>()).add(o);
            if (a == null) {
                a = r;
            }
            b = r;
        }
        // the same choice on every server and every run: by recipe id
        for (List<Opcion> list : m.values()) {
            list.sort(Comparator.comparing(o -> o.id().toString()));
        }
        indice = m;
        de = manager;
        testigoA = a;
        testigoB = b;
        return m;
    }

    private static boolean vigente(RecipeManager manager, @Nullable Recipe<?> testigo) {
        return testigo != null && manager.get(testigo.getId()).orElse(null) == testigo;
    }

    /** The ways the server knows to make {@code item} on a crafting grid. */
    public static List<Opcion> para(ServerWorld world, Item item) {
        return indice(world).getOrDefault(item, List.of());
    }

    @Nullable
    static Opcion opcion(CraftingRecipe r, DynamicRegistryManager reg) {
        if (r.isIgnoredInRecipeBook()) {
            return null; // special: its result depends on what goes in (maps, fireworks, banners, repairs)
        }
        ItemStack out = r.getOutput(reg);
        if (out == null || out.isEmpty()) {
            return null;
        }
        List<Ingredient> casillas;
        int ancho;
        int alto;
        boolean forma;
        if (r instanceof ShapedRecipe s) {
            ancho = s.getWidth();
            alto = s.getHeight();
            casillas = List.copyOf(s.getIngredients());
            forma = true;
            if (casillas.size() != ancho * alto) {
                return null;
            }
        } else if (r instanceof ShapelessRecipe) {
            casillas = List.copyOf(r.getIngredients());
            ancho = 0;
            alto = 0;
            forma = false;
            if (casillas.size() > 9) {
                return null;
            }
        } else {
            return null; // a mod's own kind of crafting recipe: it needs an adapter of its own
        }
        boolean algo = false;
        for (Ingredient ing : casillas) {
            algo |= !ing.isEmpty();
        }
        if (!algo) {
            return null;
        }
        return new Opcion(r, r.getId(), out.copy(), casillas, ancho, alto, forma, !r.fits(2, 2));
    }
}
