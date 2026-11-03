package net.mca.entity.ai.chatAI;

import net.mca.Config;
import net.mca.MCA;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Chore;
import net.mca.entity.ai.MoveState;
import net.mca.entity.ai.mente.Voluntad;
import net.mca.entity.interaction.Constraint;
import net.mca.item.ItemsMCA;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Locale;
import java.util.Set;

/** Authority, not affection. Rechecked on the server immediately before every external command. */
public final class CommandPolicy {
    public enum Source { MENU, PACKET, LOCAL, MODEL }
    public record Decision(boolean allowed, String es, String en) {
        public String line(boolean spanish) { return spanish ? es : en; }
        public String outcome() { return "You have no permission to do that: " + en; }
    }
    private static final Decision YES = new Decision(true, "", "");
    private static final Set<String> PUBLIC = Set.of("open-trade-window", "trade", "gift", "location", "apologize", "slap", "dialogue");
    private static final Set<String> CLOSE = Set.of("give-item", "open-trade-window", "trade");
    private static final Set<String> MENU = Set.of("pick_up", "ridehorse", "sethome", "gohome", "setworkplace", "trade", "inventory",
            "gift", "adopt", "procreate", "divorcePapers", "divorceConfirm", "execute", "pardon", "stay_in_village", "hire_short",
            "hire_long", "infected", "stopworking", "armor", "profession", "apologize", "location", "slap", "dialogue");

    private CommandPolicy() { }

    private static Decision no(String es, String en) { return new Decision(false, es, en); }

    public static Decision check(ServerPlayerEntity player, VillagerEntityMCA villager, String raw, Source source) {
        if (player == null || villager == null || player.getServer() == null || !player.getServer().isOnThread())
            return no("No puedo recibir esa orden ahora.", "The server cannot accept that order now.");
        if (!player.isAlive() || player.isSpectator() || !villager.isAlive() || villager.isRemoved())
            return no("Ahora no podemos hacer eso.", "We cannot do that now.");
        if (player.getWorld() != villager.getWorld())
            return no("Estamos en dimensiones distintas.", "We are in different dimensions.");
        if (raw == null || raw.length() > 256) return no("No entiendo esa orden.", "That order is invalid.");
        String command = raw.split("\\.", 2)[0];
        boolean chat = source == Source.LOCAL || source == Source.MODEL;
        if (chat && !Config.getInstance().villagerChatAIUseTools)
            return no("Las acciones por chat están desactivadas.", "Chat actions are disabled.");
        boolean assigned = villager.getVillagerBrain().getJobAssignerUuid().filter(player.getUuid()::equals).isPresent();
        boolean family = Constraint.FAMILY.test(villager, player);
        boolean operator = player.hasPermissionLevel(2);
        if ("call-player".equals(command)) {
            return family || operator ? YES : no("Solo mi familia puede llamarme así.", "Only my family can summon me.");
        }
        double range = chat && !CLOSE.contains(command) ? 64 : 8;
        if (villager.squaredDistanceTo(player) > range * range && !(chat && assigned && !CLOSE.contains(command)))
            return no("Estás demasiado lejos para pedirme eso.", "You are too far away for that action.");
        if ("editor".equals(command)) {
            return operator || player.getMainHandStack().isOf(ItemsMCA.VILLAGER_EDITOR.get())
                    || player.getOffHandStack().isOf(ItemsMCA.VILLAGER_EDITOR.get()) ? YES
                    : no("Necesitas el editor de aldeanos.", "You need the villager editor.");
        }
        if (source == Source.MENU || source == Source.PACKET) {
            if (!MENU.contains(command) && MoveState.byCommand(command).isEmpty() && Chore.byCommand(command).isEmpty())
                return no("Esa orden no existe.", "That command does not exist.");
            if ("inventory".equals(command) && !family && !Constraint.MERCENARY.test(villager, player) && !operator)
                return no("No tienes acceso a mi mochila.", "You cannot access my inventory.");
            if ("adopt".equals(command) && !Constraint.ORPHAN.test(villager, player))
                return no("No se cumplen las condiciones para adoptar.", "The adoption requirements are not met.");
            if (Set.of("procreate", "divorcePapers", "divorceConfirm").contains(command) && !Constraint.SPOUSE.test(villager, player))
                return no("No somos cónyuges.", "We are not spouses.");
            if (Set.of("execute", "pardon").contains(command) && !operator && !Constraint.MAYOR.test(villager, player))
                return no("No tienes permiso para cambiar eso.", "You cannot change that status.");
            if ("profession".equals(command) && !operator && !Constraint.MONARCH.test(villager, player))
                return no("No tienes permiso para cambiar mi profesión.", "You cannot change my profession.");
            if ("infected".equals(command) && !operator)
                return no("Esa acción requiere permisos de operador.", "That action requires operator permissions.");
            if (command.startsWith("hire_") && player.getInventory().count(Items.EMERALD) < (command.equals("hire_short") ? 5 : 10))
                return no("No tienes las esmeraldas necesarias.", "You do not have enough emeralds.");
        } else if (TriggerCommandInfos.triggerCommands.stream().noneMatch(c -> c.command.equals(command))) {
            return no("Esa orden no existe.", "That command does not exist.");
        }
        if ((command.equals("trade") || command.equals("open-trade-window"))
                && (!Constraint.ADULT.test(villager, player) || !Constraint.TRADER.test(villager, player)))
            return no("Ahora no puedo comerciar.", "I cannot trade now.");
        if (command.equals("pick_up") && (!Constraint.NOT_ADULT.test(villager, player) || !Constraint.NOT_TEEN.test(villager, player)))
            return no("No puedes llevarme así.", "You cannot carry me like that.");
        if (Chore.byCommand(command).filter(c -> c != Chore.NONE).isPresent() && Constraint.BABY.test(villager, player))
            return no("Todavía soy demasiado pequeño para trabajar.", "I am too young to work.");
        if (!PUBLIC.contains(command) && !operator && villager.getVillagerBrain().getCurrentJob() != Chore.NONE
                && villager.getVillagerBrain().getJobAssignerUuid().filter(id -> !id.equals(player.getUuid())).isPresent())
            return no("Ya tengo un compromiso con otra persona.", "Another player assigned my current task.");
        return YES;
    }

    public static void report(VillagerEntityMCA villager, String command, Decision decision) {
        MCA.LOGGER.info("[Tropeles] resultado DENEGADO {} {}: {}", villager.getUuid(), command, decision.en());
    }

    public static boolean menu(ServerPlayerEntity player, VillagerEntityMCA villager, String command) {
        Decision decision = check(player, villager, command, Source.MENU);
        if (!decision.allowed()) {
            report(villager, command, decision);
            ChatAI.deliver(player, villager, decision.line(Voluntad.of(villager).spanish()));
        }
        return decision.allowed();
    }
}
