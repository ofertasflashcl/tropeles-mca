package net.mca.server.command;

import com.mojang.brigadier.CommandDispatcher;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.obra.Constructor;
import net.mca.entity.ai.obra.Obra;
import net.mca.entity.ai.obra.ObrasMundo;
import net.mca.entity.ai.obra.Plano;
import net.mca.entity.ai.obra.Proyeccion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * /mca-obra: the houses under way in this world (who builds them, for whom, where, how far along);
 * /mca-obra materiales: everything the house of the nearest builder still needs goes into its bag (to watch it
 * build without the hours of gathering); /mca-obra cancelar: the nearest house is given up (what stands stays).
 */
public final class ObraCommand {
    private ObraCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("mca-obra")
                .requires(s -> s.hasPermissionLevel(2) || s.getPlayer() != null && s.getServer().isHost(s.getPlayer().getGameProfile()))
                .executes(ctx -> ver(ctx.getSource()))
                .then(CommandManager.literal("materiales").executes(ctx -> materiales(ctx.getSource())))
                .then(CommandManager.literal("cancelar").executes(ctx -> cancelar(ctx.getSource()))));
    }

    private static int ver(ServerCommandSource source) {
        ServerWorld world = source.getWorld();
        ObrasMundo obras = ObrasMundo.get(world);
        if (obras.todas().isEmpty()) {
            source.sendFeedback(() -> Text.literal("No hay casas en obra en este mundo."), false);
            return 0;
        }
        for (Obra o : obras.todas()) {
            int total = o.total();
            int done = o.hechas(world);
            String line = o.constructorNombre + " → " + (o.paraNombre.isEmpty() ? "?" : o.paraNombre) + ": casa de " + o.madera.es + " en " + o.donde()
                    + ", " + done + "/" + total + " (" + (total == 0 ? 100 : done * 100 / total) + "%)" + (o.pausaJugador ? ", en pausa" : "")
                    + (o.sinVidrio ? ", sin vidrios" : "") + ", " + o.andamios.size() + " andamios";
            source.sendFeedback(() -> Text.literal(line), false);
        }
        return obras.todas().size();
    }

    /** The builder whose house is nearest to whoever runs the command. */
    @Nullable
    private static VillagerEntityMCA cercano(ServerCommandSource source, Obra[] obra) {
        ServerWorld world = source.getWorld();
        Obra best = null;
        double bestD = Double.MAX_VALUE;
        for (Obra o : ObrasMundo.get(world).todas()) {
            double d = o.centro().getSquaredDistance(source.getPosition());
            if (d < bestD) {
                bestD = d;
                best = o;
            }
        }
        if (best == null) {
            return null;
        }
        obra[0] = best;
        return world.getEntity(best.constructor) instanceof VillagerEntityMCA v ? v : null;
    }

    private static int materiales(ServerCommandSource source) {
        ServerWorld world = source.getWorld();
        Obra[] o = new Obra[1];
        VillagerEntityMCA v = cercano(source, o);
        if (o[0] == null) {
            source.sendError(Text.literal("No hay casas en obra en este mundo."));
            return 0;
        }
        if (v == null) {
            source.sendError(Text.literal(o[0].constructorNombre + " no está cargado cerca."));
            return 0;
        }
        Map<Plano.Rol, Integer> f = o[0].faltan(world);
        int given = 0;
        for (Map.Entry<Plano.Rol, Integer> e : f.entrySet()) {
            given += dar(v, new ItemStack(Plano.item(e.getKey(), o[0].madera), e.getValue()));
        }
        given += dar(v, new ItemStack(Items.DIRT, 24)); // scaffolds and fill
        int n = given;
        source.sendFeedback(() -> Text.literal("Le di a " + v.getName().getString() + " " + n + " cosas para la casa: "
                + Constructor.faltas(world, v, o[0], true)), true);
        return n;
    }

    private static int dar(VillagerEntityMCA v, ItemStack stack) {
        int n = stack.getCount();
        while (!stack.isEmpty()) {
            ItemStack part = stack.split(Math.min(stack.getMaxCount(), stack.getCount()));
            ItemStack left = v.getInventory().addStack(part);
            if (!left.isEmpty()) {
                v.dropStack(left);
            }
        }
        return n;
    }

    private static int cancelar(ServerCommandSource source) {
        ServerWorld world = source.getWorld();
        Obra[] o = new Obra[1];
        VillagerEntityMCA v = cercano(source, o);
        if (o[0] == null) {
            source.sendError(Text.literal("No hay casas en obra en este mundo."));
            return 0;
        }
        ServerPlayerEntity player = source.getPlayer();
        if (v != null && player != null) {
            String said = net.mca.entity.ai.mente.Voluntad.cancelarObra(v, player, true);
            source.sendFeedback(() -> Text.literal(said), false);
        } else {
            ObrasMundo.get(world).quitar(o[0]);
            Proyeccion.borrar(world, o[0]);
            source.sendFeedback(() -> Text.literal("Casa de " + o[0].constructorNombre + " cancelada."), true);
        }
        return 1;
    }
}
