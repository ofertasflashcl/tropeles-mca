package net.mca.entity.ai;

import com.google.gson.JsonObject;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * The long term memory stored String keys for a given amount of time
 * While not powerful in terms of features it allows adding more intelligence to villager interactions
 */
public class LongTermMemory {
    final HashMap<String, Long> memories = new HashMap<>();

    private final VillagerEntityMCA entity;

    public LongTermMemory(VillagerEntityMCA entity) {
        this.entity = entity;
    }

    public void writeToNbt(NbtCompound nbt) {
        NbtCompound memory = new NbtCompound();
        for (Map.Entry<String, Long> entry : memories.entrySet()) {
            memory.putLong(entry.getKey(), entry.getValue());
        }
        nbt.put("longTermMemory", memory);
    }

    public void readFromNbt(NbtCompound nbt) {
        NbtCompound memory = nbt.getCompound("longTermMemory");
        memories.clear();
        for (String key : memory.getKeys()) {
            memories.put(key, memory.getLong(key));
        }
    }

    //remember forever
    public void remember(String id) {
        memories.put(id, Long.MAX_VALUE);
    }

    public void remember(String id, long time) {
        long currentTime = entity.getWorld().getTime();
        if (memories.containsKey(id)) {
            currentTime = Math.max(currentTime, memories.get(id));
        }
        if (currentTime == Long.MAX_VALUE) { memories.put(id, Long.MAX_VALUE); return; }
        long expires;
        try { expires = Math.addExact(currentTime, time); }
        catch (ArithmeticException overflow) { expires = time >= 0 ? Long.MAX_VALUE : Long.MIN_VALUE; }
        memories.put(id, expires);
    }

    public long getMemory(String id) {
        if (memories.containsKey(id)) {
            if (memories.get(id) == Long.MAX_VALUE) return Long.MAX_VALUE;
            if (entity.getWorld().getTime() >= memories.get(id)) {
                memories.remove(id);
            } else {
                try { return Math.subtractExact(memories.get(id), entity.getWorld().getTime()); }
                catch (ArithmeticException overflow) { return Long.MAX_VALUE; }
            }
        }
        return 0;
    }

    public boolean hasMemory(String id) {
        return getMemory(id) > 0;
    }

    public static String parseId(JsonObject json, ServerPlayerEntity player) {
        String id = json.get("id").getAsString();
        if (json.has("var")) {
            switch (json.get("var").getAsString()) {
                case "player" -> {
                    assert player != null;
                    id += "." + player.getUuid().toString();
                }
            }
        }
        return id;
    }
}
