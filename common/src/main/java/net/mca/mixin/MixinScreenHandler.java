package net.mca.mixin;

import net.mca.entity.ai.body.Cofres;
import net.mca.item.BabyItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenHandler.class)
abstract class MixinScreenHandler {
    @Shadow @Final public DefaultedList<Slot> slots;

    @Shadow public abstract ItemStack getCursorStack();

    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo info) {
        ItemStack stack = mca$getDroppedStack(slotIndex, actionType, player);
        if (BabyItem.shouldCancelDrop(stack, player)) {
            info.cancel();
        }
    }

    @Unique private java.util.List<Cofres.ClickSnapshot> mca$before = java.util.List.of();

    @Inject(method = "internalOnSlotClick", at = @At("HEAD"))
    private void mca$beforeClick(int slot, int button, SlotActionType action, PlayerEntity player, CallbackInfo info) {
        mca$before = player instanceof ServerPlayerEntity p ? Cofres.beforeClick(p, (ScreenHandler)(Object)this) : java.util.List.of();
    }

    @Inject(method = "internalOnSlotClick", at = @At("RETURN"))
    private void mca$afterClick(int slot, int button, SlotActionType action, PlayerEntity player, CallbackInfo info) {
        if (player instanceof ServerPlayerEntity p) Cofres.afterClick(p, mca$before);
        mca$before = java.util.List.of();
    }

    @Unique
    private ItemStack mca$getDroppedStack(int slotIndex, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex == ScreenHandler.EMPTY_SPACE_SLOT_INDEX && actionType == SlotActionType.PICKUP) {
            return getCursorStack();
        }
        if (slotIndex >= 0 && slotIndex < slots.size() && actionType == SlotActionType.THROW) {
            Slot slot = slots.get(slotIndex);
            if (slot.canTakeItems(player)) {
                return slot.getStack();
            }
        }
        return ItemStack.EMPTY;
    }
}
