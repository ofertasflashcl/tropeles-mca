package net.mca.entity.ai.chatAI.inworldAIModules;

import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.chatAI.TriggerCommandInfos;
import net.mca.entity.ai.chatAI.inworldAIModules.api.Interaction;
import net.mca.entity.ai.chatAI.inworldAIModules.api.TriggerEvent;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Class to manage command triggers (wear armor, follow me, etc.)
 */
public class TriggerModule {
    /**
     * Looks for outgoing triggers from the last interaction
     * and executes {@link TriggerCommandInfos specific actions} associated with different trigger names
     *
     * @param interaction Interaction object from a SendText request
     * @param player      Player in the conversation
     * @param villager    Villager in the conversation
     */
    public void processTriggers(Interaction interaction, ServerPlayerEntity player, VillagerEntityMCA villager) {
        processTriggers(interaction, player, villager, null);
    }

    public String processTriggers(Interaction interaction, ServerPlayerEntity player, VillagerEntityMCA villager,
                                net.mca.entity.ai.chatAI.ActionTurn lease) {
        for (TriggerEvent event : interaction.outgoingTriggers()) {
            var found = TriggerCommandInfos.findCommand(event.trigger(), player, villager);
            if (found.isEmpty()) continue;
            var command = found.get();
            boolean es = net.mca.entity.ai.mente.Voluntad.of(villager).spanish();
            var permission = net.mca.entity.ai.chatAI.CommandPolicy.check(player, villager, command.command,
                    net.mca.entity.ai.chatAI.CommandPolicy.Source.MODEL);
            if (!permission.allowed()) {
                net.mca.entity.ai.chatAI.CommandPolicy.report(villager, command.command, permission);
                return permission.line(es);
            }
            if (lease != null && !lease.claimAction()) return null;
            String outcome;
            try { outcome = command.run(player, villager, ""); }
            finally { if (lease != null) lease.acknowledgeAction(); }
            if (outcome != null && outcome.startsWith("You have no")) {
                return net.mca.entity.ai.chatAI.MenteLocal.answer(player, villager,
                        new net.mca.entity.ai.chatAI.MenteLocal.Intent(command.command, "", null, es), outcome, null, null, false);
            }
        }
        return null;
    }
}
