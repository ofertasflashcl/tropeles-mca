package net.mca.server.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.chatAI.Acciones;
import net.mca.entity.ai.nucleo.Nucleo;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * /mca-nucleo [nombre]: the inner life of the nearest villager (or the one named, within 128 blocks): its traits,
 * its drives, what holds its attention, the incidents that weigh most with how it knows each, and its beliefs.
 */
public final class NucleoCommand {
    private NucleoCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("mca-nucleo")
                .requires(s -> s.hasPermissionLevel(2) || s.getPlayer() != null && s.getServer().isHost(s.getPlayer().getGameProfile()))
                .executes(ctx -> ver(ctx.getSource(), null))
                .then(CommandManager.argument("nombre", StringArgumentType.greedyString())
                        .executes(ctx -> ver(ctx.getSource(), StringArgumentType.getString(ctx, "nombre")))));
    }

    private static int ver(ServerCommandSource source, @Nullable String nombre) {
        ServerWorld world = source.getWorld();
        Vec3d at = source.getPosition();
        String want = nombre == null ? null : Acciones.normalize(nombre.trim());
        List<VillagerEntityMCA> near = world.getEntitiesByClass(VillagerEntityMCA.class, new Box(at, at).expand(nombre == null ? 32 : 128),
                v -> v.isAlive() && (want == null || Acciones.normalize(v.getName().getString()).startsWith(want)));
        VillagerEntityMCA v = near.stream().min(Comparator.comparingDouble(x -> x.squaredDistanceTo(at))).orElse(null);
        if (v == null) {
            source.sendFeedback(() -> Text.literal(nombre == null ? "No hay aldeanos cerca." : "No encuentro a " + nombre + " cerca."), false);
            return 0;
        }
        for (String line : Nucleo.informe(world, v)) {
            source.sendFeedback(() -> Text.literal(line), false);
        }
        return 1;
    }
}
