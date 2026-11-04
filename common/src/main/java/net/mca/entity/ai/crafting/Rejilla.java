package net.mca.entity.ai.crafting;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeMatcher;
import net.minecraft.util.collection.DefaultedList;

import java.util.List;

/**
 * The crafting grid a villager lays its things out on: the 2x2 of its hands or the 3x3 of a table it stands at.
 * It belongs to no screen; the server's own recipes read it exactly as they read a player's grid.
 */
final class Rejilla implements RecipeInputInventory {
    private final int ancho;
    private final int alto;
    private final DefaultedList<ItemStack> stacks;

    Rejilla(int ancho, int alto) {
        this.ancho = ancho;
        this.alto = alto;
        this.stacks = DefaultedList.ofSize(ancho * alto, ItemStack.EMPTY);
    }

    @Override
    public int size() {
        return stacks.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        return slot >= 0 && slot < stacks.size() ? stacks.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        return Inventories.splitStack(stacks, slot, amount);
    }

    @Override
    public ItemStack removeStack(int slot) {
        return Inventories.removeStack(stacks, slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        stacks.set(slot, stack);
    }

    @Override
    public void markDirty() {
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return true;
    }

    @Override
    public void clear() {
        stacks.clear();
    }

    @Override
    public int getWidth() {
        return ancho;
    }

    @Override
    public int getHeight() {
        return alto;
    }

    @Override
    public List<ItemStack> getInputStacks() {
        return List.copyOf(stacks);
    }

    @Override
    public void provideRecipeInputs(RecipeMatcher finder) {
        for (ItemStack stack : stacks) {
            finder.addUnenchantedInput(stack);
        }
    }
}
