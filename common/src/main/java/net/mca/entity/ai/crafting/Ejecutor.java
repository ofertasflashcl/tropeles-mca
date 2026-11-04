package net.mca.entity.ai.crafting;

import net.mca.MCA;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.sense.Retina;
import net.minecraft.block.Blocks;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Crafts for real, on the server thread: the bag's own stacks go on a 2x2 grid (its hands) or a 3x3 one (a table it
 * stands at), the server's recipe is asked again right then, and only when it matches, and what comes out fits, is
 * anything taken. Then the ingredients go, the result comes in, and what a recipe leaves behind (the buckets of a cake)
 * goes back into the bag or falls at its feet. Nothing is made out of nothing, nothing twice.
 */
public final class Ejecutor {
    /** How a craft went. */
    public enum Tipo {
        /** Made. */
        HECHO,
        /** Something it needs is not in its bag (or is its own, or kept back for another job). */
        FALTA,
        /** That recipe takes the 3x3 grid of a crafting table and there is none within reach. */
        SIN_MESA,
        /** What would come out has no room in the bag: nothing was used. */
        LLENO,
        /** The bag or the server's recipes are not what the plan was made with. */
        CAMBIO,
        /** The server has no such recipe. */
        SIN_RECETA
    }

    /** The outcome, with what was made of the item asked for (empty unless {@link Tipo#HECHO}) and why not. */
    public record Resultado(Tipo tipo, ItemStack hecho, String motivo) {
        public boolean ok() {
            return tipo == Tipo.HECHO;
        }

        static Resultado no(Tipo tipo, String motivo) {
            return new Resultado(tipo, ItemStack.EMPTY, motivo);
        }
    }

    private Ejecutor() {
    }

    /** A crafting table it can work at: within an arm's reach and in its sight, through no wall. */
    public static boolean mesaCerca(ServerWorld world, VillagerEntityMCA villager) {
        for (BlockPos p : BlockPos.iterateOutwards(villager.getBlockPos(), 5, 3, 5)) {
            if (world.getBlockState(p).isOf(Blocks.CRAFTING_TABLE)
                    && Retina.visibleFace(world, villager, p.toImmutable(), Retina.BLOCK_REACH) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Makes {@code n} (at least) of {@code item} from what the bag holds, the steps it takes first (a hay bale into wheat
     * for bread). {@code mesa}: a crafting table is within reach. Planned again once if the plan no longer fits the bag.
     */
    public static Resultado hacer(ServerWorld world, VillagerEntityMCA villager, Item item, int n, boolean mesa, Reserva reserva) {
        return hacer(world, villager, item, n, mesa, reserva, false);
    }

    /**
     * The same; with {@code alSuelo} what has no room in the bag falls at its feet instead of the craft being refused
     * ({@link Tipo#LLENO}), the way its work always went: nothing is lost either way.
     */
    public static Resultado hacer(ServerWorld world, VillagerEntityMCA villager, Item item, int n, boolean mesa, Reserva reserva, boolean alSuelo) {
        int hecho = 0;
        Resultado ultimo = Resultado.no(Tipo.SIN_RECETA, "no sabe hacer " + nombre(item));
        for (int intento = 0; intento < 2 && hecho < n; intento++) {
            Planificador.Plan plan = Planificador.planear(world, villager.getInventory(), item, n - hecho, reserva, mesa);
            if (!plan.posible()) {
                if (!mesa && Planificador.planear(world, villager.getInventory(), item, n - hecho, reserva, true).posible()) {
                    return Resultado.no(Tipo.SIN_MESA, nombre(item) + " necesita una mesa de crafteo");
                }
                Item falta = plan.falta();
                return Resultado.no(Recetario.para(world, item).isEmpty() ? Tipo.SIN_RECETA : Tipo.FALTA,
                        "le falta " + nombre(falta == null ? item : falta) + " para " + nombre(item));
            }
            if (plan.mesa() && !mesa) {
                return Resultado.no(Tipo.SIN_MESA, nombre(item) + " necesita una mesa de crafteo");
            }
            ultimo = seguir(world, villager, plan, mesa, reserva, item, alSuelo);
            hecho += ultimo.hecho().isOf(item) ? ultimo.hecho().getCount() : 0;
            if (ultimo.tipo() != Tipo.CAMBIO && ultimo.tipo() != Tipo.FALTA) {
                break;
            }
        }
        if (hecho > 0 && hecho >= n) {
            return new Resultado(Tipo.HECHO, new ItemStack(item, hecho), "");
        }
        return hecho > 0 ? new Resultado(ultimo.tipo(), new ItemStack(item, hecho), ultimo.motivo()) : ultimo;
    }

    /** Carries out a plan made earlier, every step checked again against the bag and the server as it is now. */
    public static Resultado seguir(ServerWorld world, VillagerEntityMCA villager, Planificador.Plan plan, boolean mesa, Reserva reserva, Item meta) {
        return seguir(world, villager, plan, mesa, reserva, meta, false);
    }

    private static Resultado seguir(ServerWorld world, VillagerEntityMCA villager, Planificador.Plan plan, boolean mesa, Reserva reserva, Item meta,
                                    boolean alSuelo) {
        int hecho = 0;
        for (Planificador.Paso paso : plan.pasos()) {
            for (int k = 0; k < paso.veces(); k++) {
                Resultado r = una(world, villager, paso.opcion(), mesa, reserva, alSuelo);
                if (!r.ok()) {
                    return new Resultado(r.tipo(), hecho > 0 ? new ItemStack(meta, hecho) : ItemStack.EMPTY, r.motivo());
                }
                if (r.hecho().isOf(meta)) {
                    hecho += r.hecho().getCount();
                }
            }
        }
        return hecho > 0 ? new Resultado(Tipo.HECHO, new ItemStack(meta, hecho), "")
                : Resultado.no(Tipo.CAMBIO, "el plan no dio " + nombre(meta));
    }

    /** One craft of {@code o}, now. */
    public static Resultado una(ServerWorld world, VillagerEntityMCA villager, Recetario.Opcion o, boolean mesa, Reserva reserva) {
        return una(world, villager, o, mesa, reserva, false);
    }

    private static Resultado una(ServerWorld world, VillagerEntityMCA villager, Recetario.Opcion o, boolean mesa, Reserva reserva, boolean alSuelo) {
        if (o.mesa() && !mesa) {
            return Resultado.no(Tipo.SIN_MESA, nombre(o.item()) + " necesita una mesa de crafteo");
        }
        SimpleInventory inv = villager.getInventory();
        int[] libres = reserva.usables(inv);
        int lado = mesa ? 3 : 2;
        Rejilla grid = new Rejilla(lado, lado);
        int[] tomado = new int[lado * lado];
        Arrays.fill(tomado, -1);
        Map<Ingredient, Item> elegido = new IdentityHashMap<>();
        List<Ingredient> casillas = o.casillas();
        if (o.conForma()) {
            if (o.ancho() > lado || o.alto() > lado) {
                return Resultado.no(Tipo.SIN_MESA, nombre(o.item()) + " necesita una mesa de crafteo");
            }
            for (int y = 0; y < o.alto(); y++) {
                for (int x = 0; x < o.ancho(); x++) {
                    Ingredient ing = casillas.get(y * o.ancho() + x);
                    if (!ing.isEmpty() && !poner(inv, libres, grid, tomado, y * lado + x, ing, elegido)) {
                        return Resultado.no(Tipo.FALTA, "le falta " + nombreDe(ing) + " para " + nombre(o.item()));
                    }
                }
            }
        } else {
            int celda = 0;
            for (Ingredient ing : casillas) {
                if (ing.isEmpty()) {
                    continue;
                }
                if (celda >= lado * lado) {
                    return Resultado.no(Tipo.SIN_MESA, nombre(o.item()) + " necesita una mesa de crafteo");
                }
                if (!poner(inv, libres, grid, tomado, celda, ing, elegido)) {
                    return Resultado.no(Tipo.FALTA, "le falta " + nombreDe(ing) + " para " + nombre(o.item()));
                }
                celda++;
            }
        }
        // the server decides, now: a datapack may have changed or removed the recipe since the plan was made
        RecipeManager manager = world.getRecipeManager();
        Optional<CraftingRecipe> match = manager.getFirstMatch(RecipeType.CRAFTING, grid, world);
        DynamicRegistryManager reg = world.getRegistryManager();
        if (match.isEmpty()) {
            return Resultado.no(Tipo.CAMBIO, "el servidor ya no tiene la receta de " + nombre(o.item()));
        }
        CraftingRecipe receta = match.get();
        if (receta != o.receta() && !receta.getId().equals(o.id()) && !receta.getOutput(reg).isOf(o.item())) {
            return Resultado.no(Tipo.CAMBIO, "eso hace " + nombre(receta.getOutput(reg).getItem()) + " ahora, no " + nombre(o.item()));
        }
        ItemStack out = receta.craft(grid, reg);
        if (out.isEmpty()) {
            return Resultado.no(Tipo.SIN_RECETA, "la receta de " + nombre(o.item()) + " no dio nada");
        }
        DefaultedList<ItemStack> restos = receta.getRemainder(grid);
        // room for the result once the ingredients are gone, tried on a copy: a full bag uses nothing up
        SimpleInventory prueba = new SimpleInventory(inv.size());
        for (int i = 0; i < inv.size(); i++) {
            prueba.setStack(i, inv.getStack(i).copy());
        }
        for (int slot : tomado) {
            if (slot >= 0) {
                prueba.getStack(slot).decrement(1);
            }
        }
        if (!alSuelo && !prueba.addStack(out.copy()).isEmpty()) {
            return Resultado.no(Tipo.LLENO, "no le cabe " + nombre(out.getItem()) + " en la mochila");
        }
        Map<String, Integer> gastado = new LinkedHashMap<>();
        for (int slot : tomado) {
            if (slot >= 0) {
                ItemStack s = inv.getStack(slot);
                gastado.merge(Registries.ITEM.getId(s.getItem()).toString(), 1, Integer::sum);
                s.decrement(1);
            }
        }
        ItemStack sobra = inv.addStack(out.copy());
        if (!sobra.isEmpty()) {
            villager.dropStack(sobra);
        }
        StringBuilder quedan = new StringBuilder();
        for (ItemStack resto : restos) {
            if (resto.isEmpty()) {
                continue;
            }
            quedan.append(quedan.length() == 0 ? "" : ", ").append(resto.getCount()).append(' ').append(Registries.ITEM.getId(resto.getItem()));
            ItemStack left = inv.addStack(resto.copy());
            if (!left.isEmpty()) {
                villager.dropStack(left);
            }
        }
        inv.markDirty();
        villager.swingHand(Hand.MAIN_HAND);
        MCA.LOGGER.info("[Tropeles] receta {}: {} gastó {} y sacó {} {}{}", receta.getId(), villager.getName().getString(), gastado,
                out.getCount(), Registries.ITEM.getId(out.getItem()), quedan.length() == 0 ? "" : "; le quedan " + quedan);
        return new Resultado(Tipo.HECHO, out.copy(), "");
    }

    /**
     * Makes whatever {@code salida} accepts out of the stack in {@code slot} alone (a log into the planks the server says
     * it gives), by hand. Empty when no such recipe takes it.
     */
    public static Resultado desde(ServerWorld world, VillagerEntityMCA villager, int slot, Predicate<ItemStack> salida, Reserva reserva) {
        return desde(world, villager, slot, salida, reserva, false);
    }

    /** The same; with {@code alSuelo} what has no room falls at its feet. */
    public static Resultado desde(ServerWorld world, VillagerEntityMCA villager, int slot, Predicate<ItemStack> salida, Reserva reserva, boolean alSuelo) {
        ItemStack stack = villager.getInventory().getStack(slot);
        if (stack.isEmpty()) {
            return Resultado.no(Tipo.FALTA, "no tiene nada en ese bolsillo");
        }
        for (List<Recetario.Opcion> opciones : Recetario.indice(world).values()) {
            for (Recetario.Opcion o : opciones) {
                if (o.mesa() || !salida.test(o.salida())) {
                    continue;
                }
                List<Ingredient> ings = o.ingredientes();
                if (ings.size() == 1 && ings.get(0).test(stack)) {
                    return una(world, villager, o, false, reserva, alSuelo);
                }
            }
        }
        return Resultado.no(Tipo.SIN_RECETA, "no hay receta que haga eso con " + nombre(stack.getItem()));
    }

    /** One cell of the grid filled from the bag: the same item as the cell before it for the same ingredient when it can. */
    private static boolean poner(SimpleInventory inv, int[] libres, Rejilla grid, int[] tomado, int celda, Ingredient ing, Map<Ingredient, Item> elegido) {
        Item antes = elegido.get(ing);
        int slot = -1;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (libres[i] <= 0 || s.isEmpty() || !ing.test(s)) {
                continue;
            }
            if (slot < 0 || antes != null && s.isOf(antes) && !inv.getStack(slot).isOf(antes)) {
                slot = i;
            }
        }
        if (slot < 0) {
            return false;
        }
        libres[slot]--;
        tomado[celda] = slot;
        grid.setStack(celda, inv.getStack(slot).copyWithCount(1));
        elegido.put(ing, inv.getStack(slot).getItem());
        return true;
    }

    static String nombre(@Nullable Item item) {
        return item == null ? "nada" : item.getName().getString();
    }

    private static String nombreDe(Ingredient ing) {
        ItemStack[] m = ing.getMatchingStacks();
        return m.length == 0 ? "algo" : m[0].getName().getString();
    }
}
