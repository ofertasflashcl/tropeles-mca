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

    public void processTriggers(Interaction interaction, ServerPlayerEntity player, VillagerEntityMCA villager,
                                net.mca.entity.ai.chatAI.ActionTurn lease) {
        for (TriggerEvent event : interaction.outgoingTriggers()) {
            TriggerCommandInfos.findCommand(event.trigger(), player, villager).ifPresent(command -> {
                if (lease != null && !lease.claimAction()) return;
                try { command.run(player, villager, ""); }
                finally { if (lease != null) lease.acknowledgeAction(); }
            });
        }
    }
}
