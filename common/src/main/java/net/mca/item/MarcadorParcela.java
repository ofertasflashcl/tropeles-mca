package net.mca.item;

import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.mente.Voluntad;
import net.mca.entity.ai.obra.Propuestas;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** A plot marker with the redstone-block model. Only the bound proposal can consume it. */
public final class MarcadorParcela extends Item {
    public MarcadorParcela(Settings settings) {
        super(settings);
    }

    public static boolean entregar(ServerWorld world, PlayerEntity player, Propuestas.Propuesta p) {
        ItemStack stack = ItemStack.EMPTY;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack current = player.getInventory().getStack(slot);
            if (coincide(current, world, player, p.id)) {
                stack = current;
                break;
            }
        }
        boolean nuevo = stack.isEmpty();
        if (nuevo) {
            stack = new ItemStack(ItemsMCA.MARCADOR_PARCELA.get());
        }
        NbtCompound n = stack.getOrCreateNbt();
        n.putString("propuesta", p.id);
        n.putString("mundo", world.getRegistryKey().getValue().toString());
        n.putUuid("dueño", player.getUuid());
        n.putLong("centro", p.plano().centro.asLong());
        if (nuevo && !player.getInventory().insertStack(stack)) {
            return false;
        }
        player.getInventory().markDirty();
        return true;
    }

    public static boolean coincide(ItemStack stack, World world, PlayerEntity player, String id) {
        NbtCompound n = stack.getNbt();
        return stack.getItem() instanceof MarcadorParcela && n != null && n.containsUuid("dueño")
                && n.getUuid("dueño").equals(player.getUuid()) && n.getString("propuesta").equals(id)
                && n.getString("mundo").equals(world.getRegistryKey().getValue().toString());
    }

    public static BlockPos centro(ItemStack stack) {
        return BlockPos.fromLong(stack.getOrCreateNbt().getLong("centro"));
    }

    @Nullable
    private static Propuestas.Propuesta pendiente(ServerWorld world, PlayerEntity player, ItemStack stack) {
        NbtCompound n = stack.getNbt();
        if (n == null || !coincide(stack, world, player, n.getString("propuesta"))) {
            return null;
        }
        Propuestas.Propuesta p = Propuestas.get(world).porId(n.getString("propuesta"));
        long ahora = world.getTime();
        return p != null && p.para.equals(player.getUuid()) && p.pendienteSitio()
                && ahora >= p.creada && ahora - p.mostrada() <= Propuestas.CADUCA ? p : null;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (context.getSide() != Direction.UP) {
            return ActionResult.FAIL;
        }
        if (!(context.getWorld() instanceof ServerWorld world)) {
            return ActionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayerEntity player)) {
            return ActionResult.FAIL;
        }
        ItemStack stack = context.getStack();
        Propuestas.Propuesta p = pendiente(world, player, stack);
        if (p == null) {
            player.sendMessage(Text.translatable("item.mca.marcador_parcela.inactivo"), true);
            return ActionResult.FAIL;
        }
        if (!(world.getEntity(p.constructor) instanceof VillagerEntityMCA villager)) {
            player.sendMessage(Text.translatable("item.mca.marcador_parcela.aldeano"), true);
            return ActionResult.FAIL;
        }
        if (!Voluntad.elegirParcela(world, villager, player, p, context.getBlockPos())) {
            return ActionResult.FAIL;
        }
        stack.decrement(1);
        player.getInventory().markDirty();
        return ActionResult.CONSUME;
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!(world instanceof ServerWorld serverWorld) || !(entity instanceof ServerPlayerEntity player)
                || entity.age % 40 != 0 || stack.getNbt() == null) {
            return;
        }
        String id = stack.getNbt().getString("propuesta");
        if (coincide(stack, world, player, id) && pendiente(serverWorld, player, stack) == null) {
            stack.decrement(1);
        }
    }
}
