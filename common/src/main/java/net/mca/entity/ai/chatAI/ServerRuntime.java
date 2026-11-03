package net.mca.entity.ai.chatAI;

import net.minecraft.server.MinecraftServer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Session-scoped request deduplication. Values intentionally do not retain the server key. */
public final class ServerRuntime {
    private static final Map<MinecraftServer, ServerRuntime> SERVERS = new WeakHashMap<>();
    private final LinkedHashMap<String, Long> requests = new LinkedHashMap<>();
    private boolean closed;

    private static synchronized ServerRuntime of(MinecraftServer server) {
        return SERVERS.computeIfAbsent(server, ignored -> new ServerRuntime());
    }

    public static synchronized boolean closed(MinecraftServer server) {
        return of(server).closed;
    }

    /** Identical delivery of the same timestamped message is a retry; a later utterance is a new request. */
    public static synchronized boolean accept(MinecraftServer server, String key) {
        ServerRuntime runtime = of(server);
        if (runtime.closed) return false;
        long now = System.nanoTime();
        runtime.requests.entrySet().removeIf(e -> now - e.getValue() > 300_000_000_000L);
        if (runtime.requests.containsKey(key)) return false;
        runtime.requests.put(key, now);
        while (runtime.requests.size() > 4096) runtime.requests.remove(runtime.requests.keySet().iterator().next());
        return true;
    }

    public static synchronized void close(MinecraftServer server) {
        ServerRuntime runtime = of(server);
        runtime.closed = true;
        runtime.requests.clear();
    }
}
