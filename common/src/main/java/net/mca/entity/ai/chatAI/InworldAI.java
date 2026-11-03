package net.mca.entity.ai.chatAI;

import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.chatAI.inworldAIModules.EmotionModule;
import net.mca.entity.ai.chatAI.inworldAIModules.RelationshipModule;
import net.mca.entity.ai.chatAI.inworldAIModules.SessionModule;
import net.mca.entity.ai.chatAI.inworldAIModules.TriggerModule;
import net.mca.entity.ai.chatAI.inworldAIModules.api.Interaction;
import net.mca.entity.ai.chatAI.inworldAIModules.api.TriggerEvent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Optional;

public class InworldAI implements ChatAIStrategy {
    private final SessionModule sessionModule;
    private final RelationshipModule relationshipModule;
    private final TriggerModule triggerModule;
    private final EmotionModule emotionModule;

    public InworldAI(String resourceName) {
        this.sessionModule = new SessionModule(resourceName);
        this.relationshipModule = new RelationshipModule();
        this.triggerModule = new TriggerModule();
        this.emotionModule = new EmotionModule();
    }

    /**
     * Gets a reply from InworldAI for a given message.
     *
     * @param player   The player requesting the answer
     * @param villager The villager responding
     * @param msg      The message
     * @return {@code Optional.EMPTY} on error, Optional containing the answer to a message on success
     */
    public Optional<String> answer(ServerPlayerEntity player, VillagerEntityMCA villager, String msg) {

        return answer(player, villager, msg, null);
    }

    public Optional<String> answer(ServerPlayerEntity player, VillagerEntityMCA villager, String msg, ActionTurn existing) {
        var server = player.getServer();
        if (server == null) return Optional.empty();
        ActionTurn lease = existing != null ? existing : OpenAIChatAI.onServer(server, null,
                () -> ActionTurn.capture(player, villager, 30_000));
        if (lease == null) return Optional.empty();
        TriggerEvent event = OpenAIChatAI.onServer(server, lease, () -> new TriggerEvent("status-update", new TriggerEvent.Parameter[]{
                relationshipModule.getRelationshipTriggerParameter(player, villager), emotionModule.getEmotionTriggerParameter(villager)}));
        if (event == null) return Optional.empty();
        Optional<Interaction> response = sessionModule.getResponse(player, msg, event);
        if (response.isEmpty()) return Optional.empty();
        String line = OpenAIChatAI.onServer(server, lease, () -> {
            relationshipModule.updateRelationship(response.get(), player, villager);
            triggerModule.processTriggers(response.get(), player, villager, lease);
            return String.join("", response.get().textList());
        });
        return line == null || line.isBlank() ? Optional.empty() : Optional.of(line);
    }
}
