package net.mca.cobalt.network;

import net.minecraft.server.network.ServerPlayerEntity;

public abstract class NetworkHandler {
    private static Impl INSTANCE;

    public static <T extends Message> void registerMessage(Class<T> msg, java.util.function.Function<net.minecraft.network.PacketByteBuf, T> decoder, boolean toServer) {
        INSTANCE.registerMessage(msg, decoder, toServer);
    }

    public static void sendToServer(Message m) {
        INSTANCE.sendToServer(m);
    }

    public static void sendToPlayer(Message m, ServerPlayerEntity e) {
        INSTANCE.sendToPlayer(m, e);
    }

    public abstract static class Impl {
        protected Impl() {
            INSTANCE = this;
        }

        public abstract <T extends Message> void registerMessage(Class<T> msg, java.util.function.Function<net.minecraft.network.PacketByteBuf, T> decoder, boolean toServer);

        public abstract void sendToServer(Message m);

        public abstract void sendToPlayer(Message m, ServerPlayerEntity e);
    }
}
