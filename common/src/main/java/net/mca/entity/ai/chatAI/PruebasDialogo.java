package net.mca.entity.ai.chatAI;

import net.mca.Config;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.MoveState;
import net.mca.entity.ai.mente.Voluntad;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Pair;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Deterministic regression of the real dialogue executor; only the HTTP replies are substituted. */
public final class PruebasDialogo {
    private static final String REQUEST = "Me da miedo cruzar el bosque solo esta noche, ¿me echas una mano y caminas a mi lado?";
    private static final String STORY = "{\"message\":\"Buah, no me gusta el bosque de noche, pero hoy estoy de buenas. Vamos, no te me pierdas.\",\"optionalCommand\":\"\",\"argument\":\"\"}";
    private static final String FOLLOW = "{\"optionalCommand\":\"follow-player\",\"argument\":\"\"}";
    private static final String NONE = "{\"optionalCommand\":\"\",\"argument\":\"\"}";

    private static void require(boolean ok, String why) {
        if (!ok) throw new IllegalStateException(why);
    }

    public static void run(ServerPlayerEntity p, VillagerEntityMCA v) {
        boolean tools = Config.getInstance().villagerChatAIUseTools;
        Config.getInstance().villagerChatAIUseTools = true;
        try {
            v.getVillagerBrain().setMoveState(MoveState.STAY, p);
            Script repaired = new Script(null, STORY, FOLLOW);
            ActionTurn turn = ActionTurn.capture(p, v, 30_000);
            require(!repaired.answer(p, v, REQUEST, System.currentTimeMillis() + 10_000, null, turn).orElse("").isBlank()
                    && repaired.calls == 2 && v.getVillagerBrain().getMoveState() == MoveState.FOLLOW,
                    "Omitted action was not repaired through the real executor");
            require(repaired.answer(p, v, REQUEST, System.currentTimeMillis() + 10_000, null, turn).isEmpty()
                    && repaired.calls == 2, "Repair repeated an already completed turn");

            v.getVillagerBrain().setMoveState(MoveState.STAY, p);
            Script conversation = new Script(null, "{\"message\":\"Hola, todo bien.\"}", NONE);
            require(conversation.answer(p, v, "Hola, ¿cómo estás?").isPresent()
                    && v.getVillagerBrain().getMoveState() == MoveState.STAY, "Conversation invented an action");

            Script unknown = new Script(null, STORY, "{\"optionalCommand\":\"op-player\",\"argument\":\"\"}");
            require(unknown.answer(p, v, REQUEST).isPresent()
                    && v.getVillagerBrain().getMoveState() == MoveState.STAY, "Unlisted repair acted");
            require(OpenAIChatAI.actionChoice("{\"optionalCommand\":true,\"argument\":\"\"}", Set.of("follow-player")) == null
                    && OpenAIChatAI.actionChoice("{\"optionalCommand\":\"follow-player\",\"argument\":{},\"extra\":1}", Set.of("follow-player")) == null,
                    "Malformed action-only response accepted");

            Script stale = new Script(() -> Voluntad.nuevaGeneracion(v.getUuid()), STORY, FOLLOW);
            require(stale.answer(p, v, REQUEST).isEmpty()
                    && v.getVillagerBrain().getMoveState() == MoveState.STAY, "Stale repaired response acted or spoke");

            Script denied = new Script(() -> Config.getInstance().villagerChatAIUseTools = false, STORY, FOLLOW);
            require(denied.answer(p, v, REQUEST).orElse("").contains("desactivadas")
                    && v.getVillagerBrain().getMoveState() == MoveState.STAY, "Repair bypassed execution-time permissions");
        } finally {
            Config.getInstance().villagerChatAIUseTools = tools;
            OpenAIChatAI.forget(v.getUuid());
        }
    }

    private static final class Script extends OpenAIChatAI {
        final String[] replies;
        final Runnable beforeRepair;
        int calls;
        Script(Runnable beforeRepair, String... replies) { this.beforeRepair = beforeRepair; this.replies = replies; }

        @Override protected Cerebros.Reply askMinds(List<Cerebros.Mind> minds, Function<Cerebros.Mind, String> system,
                                                   List<Pair<String, String>> history, String msg, String playerName,
                                                   String villagerName, UUID villager, long deadline, String image) {
            if (calls == 1 && beforeRepair != null) beforeRepair.run();
            require(calls < replies.length, "Unbounded action repair retry");
            return new Cerebros.Reply("controlled-reply", replies[calls++], 0, false);
        }
    }
}
