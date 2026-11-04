package net.mca.server.command;

import net.mca.Config;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.MoveState;
import net.mca.entity.ai.body.Cofres;
import net.mca.entity.ai.brain.tasks.chore.ProspectingTask;
import net.mca.entity.ai.chatAI.*;
import net.mca.entity.ai.mente.Voluntad;
import net.mca.entity.ai.nucleo.*;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import static net.mca.server.command.Pruebas.*;

/** Regression scenarios with causal controls; run by the existing operator-only command/manifest. */
final class PruebasVida {
    static void run(ServerWorld world, String name) {
        if (name.startsWith("archivo-")) {
            try {
                if (name.equals("archivo-revisiones")) {
                    PruebasArchivo.revisions(world);
                    check(name, true, "revisión vence al reloj; escritura vieja rechazada; truncado visible; copia buena conservada");
                } else {
                    boolean prepare = name.equals("archivo-preparar");
                    int minds = PruebasArchivo.run(world, prepare);
                    int memories = net.mca.entity.ai.mente.PruebasRecuerdos.run(world, prepare);
                    check(name, minds == 1000 && memories == 1000, "1000 biografías, recuerdos y lecciones; " + (prepare ? "caché expulsada y releída" : "recuperados en segundo proceso"));
                }
            } catch (Exception e) { throw new IllegalStateException(name, e); }
            return;
        }
        if (name.equals("vida-red-codecs")) {
            PruebasPaquetes.run();
            check(name, true, "round-trips de datos tipados; rechaza objeto Java, truncado, versión, exceso, enum y basura final");
            return;
        }
        start(new Vida(world, name));
    }

    private static void require(boolean condition, String why) {
        if (!condition) throw new IllegalStateException(why);
    }

    private static final class Vida extends Scenario {
        final VillagerEntityMCA v;
        final ServerPlayerEntity p;
        final BlockPos origin;
        VillagerEntityMCA control;
        float fear;

        Vida(ServerWorld world, String name) {
            super(name, world, 2400);
            int index = switch (name) { case "vida-contratos" -> 0; case "vida-permisos" -> 1; case "vida-miedo" -> 2; case "vida-vinculo" -> 3; default -> 4; };
            origin = new BlockPos(11008 + index * 160, 100, 0);
            load(world, origin, 48);
            fill(world, origin.add(-8, -6, -8), origin.add(40, -1, 8), Blocks.STONE.getDefaultState());
            fill(world, origin.add(-8, 0, -8), origin.add(40, 4, 8), Blocks.AIR.getDefaultState());
            v = villager(world, origin, Direction.EAST, "Vida " + index);
            v.setAiDisabled(true);
            p = fakePlayer(world, "Vida_" + index, Vec3d.ofBottomCenter(origin.east(3)));
        }

        @Override Boolean step() {
            if (ticks < 40) return null;
            switch (name) {
                case "vida-contratos" -> contracts();
                case "vida-permisos" -> permissions();
                case "vida-vinculo" -> bond();
                case "robo-actor" -> theft();
                case "vida-miedo" -> { return fear(); }
                default -> throw new IllegalStateException(name);
            }
            check(name, true, state());
            return true;
        }

        void contracts() {
            Cerebros.OFFLINE.set(true);
            boolean tools = Config.getInstance().villagerChatAIUseTools;
            Config.getInstance().villagerChatAIUseTools = true;
            try {
                PruebasDialogo.run(p, v);
                ActionTurn stale = ActionTurn.capture(p, v, 30_000);
                Voluntad.nuevaGeneracion(v.getUuid());
                var rejected = new OpenAIChatAI().answer(p, v, "sígueme", System.currentTimeMillis() + 1000, null, stale);
                require(rejected.isEmpty() && v.getVillagerBrain().getMoveState() == MoveState.STAY, "Old generation acted or spoke");
                ActionTurn expired = ActionTurn.capture(p, v, 0);
                require(new OpenAIChatAI().answer(p, v, "sígueme", System.currentTimeMillis() + 1000, null, expired).isEmpty(), "Expired response accepted");
                v.getInventory().addStack(new ItemStack(Items.IRON_INGOT, 3));
                ActionTurn once = ActionTurn.capture(p, v, 30_000);
                var ai = new OpenAIChatAI();
                String line = ai.answer(p, v, "dame 1 lingote de hierro", System.currentTimeMillis() + 1000, null, once).orElse("");
                require(!line.isEmpty() && v.getInventory().count(Items.IRON_INGOT) == 2, "Initial effect not observed");
                require(ai.answer(p, v, "dame 1 lingote de hierro", System.currentTimeMillis() + 1000, null, once).isEmpty()
                        && v.getInventory().count(Items.IRON_INGOT) == 2, "Retry repeated effect or response");
                String key = "ci-duplicate-" + v.getUuid();
                require(ServerRuntime.accept(world.getServer(), key) && !ServerRuntime.accept(world.getServer(), key), "Delivery not deduplicated");
                ActionTurn identity = ActionTurn.capture(p, v, 30_000);
                var originalId = v.getUuid();
                v.setUuid(java.util.UUID.randomUUID());
                require(!identity.valid(), "Changed UUID accepted");
                v.setUuid(originalId);
                ActionTurn gone = ActionTurn.capture(p, v, 30_000);
                v.discard();
                require(!gone.valid(), "Removed actor accepted");
            } finally { Cerebros.OFFLINE.set(false); Config.getInstance().villagerChatAIUseTools = tools; }
        }

        void permissions() {
            boolean tools = Config.getInstance().villagerChatAIUseTools;
            Cerebros.OFFLINE.set(true);
            Config.getInstance().villagerChatAIUseTools = false;
            try {
                String line = new OpenAIChatAI().answer(p, v, "sígueme").orElse("");
                require(line.contains("desactivadas") && v.getVillagerBrain().getMoveState() == MoveState.STAY, "Local chat bypassed switch");
                var follow = TriggerCommandInfos.findCommand("follow-player", p, v).orElseThrow();
                require(follow.run(p, v, "").startsWith("You have no") && v.getVillagerBrain().getMoveState() == MoveState.STAY, "Model bypassed switch");
                require(CommandPolicy.check(p, v, "dialogue", CommandPolicy.Source.MENU).allowed(), "Legitimate dialogue denied");
                v.getInteractions().handle(p, "follow");
                require(v.getVillagerBrain().getMoveState() == MoveState.FOLLOW, "Menu incorrectly tied to chat switch");
                new net.mca.network.c2s.InteractionVillagerMessage("stay", v.getUuid()).receive(p);
                require(v.getVillagerBrain().getMoveState() == MoveState.STAY, "Packet did not use menu policy");
                p.refreshPositionAndAngles(origin.getX() + 40, origin.getY(), origin.getZ(), 0, 0);
                new net.mca.network.c2s.InteractionVillagerMessage("follow", v.getUuid()).receive(p);
                require(v.getVillagerBrain().getMoveState() == MoveState.STAY, "Far packet bypassed range");
            } finally { Cerebros.OFFLINE.set(false); Config.getInstance().villagerChatAIUseTools = tools; }
        }

        void bond() {
            var stranger = fakePlayer(world, "Sin_Historia", Vec3d.ofBottomCenter(origin.east(4)));
            require(Voluntad.quiere(v, p, Voluntad.Pedido.CASA).si(), "Unknown player initially rejected");
            v.getVillagerBrain().assignJob(net.mca.entity.ai.Chore.PROSPECT, p);
            Voluntad.seguir(v, p, true);
            require(Voluntad.conectado(v), "Fixture must have an active connection");
            v.damage(world.getDamageSources().playerAttack(p), 1);
            var grumble = Voluntad.quiere(v, p, Voluntad.Pedido.SEGUIR);
            require(grumble.si() && grumble.es() != null && !Voluntad.quiere(v, p, Voluntad.Pedido.CASA).si(), "One hit did not alter costly choice");
            v.timeUntilRegen = 0;
            v.damage(world.getDamageSources().playerAttack(p), 1);
            require(!Voluntad.quiere(v, p, Voluntad.Pedido.SEGUIR).si() && Voluntad.of(v).modo() == Voluntad.Modo.LIBRE, "Repeated harm did not cancel following");
            require(Voluntad.quiere(v, stranger, Voluntad.Pedido.CASA).si(), "History leaked to stranger");
            float before = Vinculo.de(world, v, p.getUuid()).conflicto();
            Nucleo.perdon(v, p, true);
            float after = Vinculo.de(world, v, p.getUuid()).conflicto();
            Nucleo.perdon(v, p, true);
            float repeated = Vinculo.de(world, v, p.getUuid()).conflicto();
            require(after < before && Math.abs(repeated - after) < 0.0001F, "Repeated apology farmed repair");
            require(Voluntad.quiere(v, p, Voluntad.Pedido.SEGUIR).si(), "Apology did not affect decision");
        }

        Boolean fear() {
            if (fear == 0) {
                control = villager(world, origin.east(32), Direction.WEST, "Control miedo");
                control.setAiDisabled(true);
                var attacker = zombie(world, Vec3d.ofBottomCenter(origin.east(4)), false);
                v.damage(world.getDamageSources().mobAttack(attacker), 1);
                attacker.discard();
                fear = Peligros.de(world, v, origin).cuanto();
                require(fear >= 0.5F, "Attack did not create fear");
                var task = new ProspectingTask();
                String blocked = task.badSpot(world, v, origin);
                String free = task.badSpot(world, control, origin);
                require(blocked != null && blocked.contains("miedo") && free == null, "Same site did not change decision by history");
                require(Peligros.de(world, control, origin).cuanto() == 0, "Uninvolved control acquired fear");
                return null;
            }
            if (ticks < 1900) return null;
            float now = Peligros.de(world, v, origin).cuanto();
            require(now < fear && !Peligros.de(world, v, origin).fuerte(), "Safe experience did not reduce fear");
            check(name, true, "miedo " + fear + " -> " + now + "; mismo sitio: rechazo con historia, permiso sin ella; recuperación sin daño");
            return true;
        }

        void theft() {
            BlockPos chest = chestFor(world, v, origin.east(2));
            BlockPos other = chestFor(world, v, origin.west(2));
            var inv = Cofres.inventory(world, chest);
            inv.setStack(0, new ItemStack(Items.DIAMOND, 10));
            Cofres.remember(world, chest);
            inv.getStack(0).decrement(2); // prior, unattributed change must not be blamed on this click
            var innocent = fakePlayer(world, "Inocente", Vec3d.ofBottomCenter(origin.east(3)));
            int innocentBefore = v.getVillagerBrain().getMemoriesForPlayer(innocent).getHearts();
            innocent.playerScreenHandler.onClosed(innocent);
            var handler = net.minecraft.screen.GenericContainerScreenHandler.createGeneric9x3(7, p.getInventory(), inv);
            int thiefBefore = v.getVillagerBrain().getMemoriesForPlayer(p).getHearts();
            handler.onSlotClick(0, 1, net.minecraft.screen.slot.SlotActionType.PICKUP, p);
            require(inv.getStack(0).getCount() == 4 && handler.getCursorStack().getCount() == 4, "Click did not transfer expected items");
            require(v.getVillagerBrain().getMemoriesForPlayer(p).getHearts() < thiefBefore, "Actual actor not identified");
            require(v.getVillagerBrain().getMemoriesForPlayer(innocent).getHearts() == innocentBefore, "Unrelated close accused innocent");
            world.setBlockState(chest, Blocks.AIR.getDefaultState(), 3);
            world.setBlockState(other, Blocks.AIR.getDefaultState(), 3);
            net.mca.server.world.data.TropelesMundo.get(world).removed(chest);
            net.mca.server.world.data.TropelesMundo.get(world).removed(other);
        }

        @Override String state() { return "efectos y controles de " + name + " comprobados"; }
        @Override void cleanup() { v.discard(); if (control != null) control.discard(); }
    }
}
