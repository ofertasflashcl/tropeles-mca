package net.mca.fabric.cobalt.network;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.mca.MCA;
import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkHandlerImpl extends NetworkHandler.Impl {
    private final Map<Class<?>, Identifier> cache = new ConcurrentHashMap<>();

    private Identifier getMessageIdentifier(Message msg) {
        return cache.computeIfAbsent(msg.getClass(), this::getMessageIdentifier);
    }

    private <T> Identifier getMessageIdentifier(Class<T> msg) {
        return MCA.locate(msg.getSimpleName().toLowerCase(Locale.ROOT));
    }

    @Override
    public <T extends Message> void registerMessage(Class<T> msg, java.util.function.Function<net.minecraft.network.PacketByteBuf, T> decoder, boolean toServer) {
        Identifier id = getMessageIdentifier(msg);

        if (toServer) ServerPlayNetworking.registerGlobalReceiver(id, (server, player, handler, buffer, responder) -> {
            Message m = Message.decode(buffer, msg, decoder);
            server.execute(() -> m.receive(player));
        });

        if (!toServer && FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            ClientProxy.register(id, msg, decoder);
        }
    }

    @Override
    public void sendToServer(Message msg) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        msg.encode(buf);
        ClientPlayNetworking.send(getMessageIdentifier(msg), buf);
    }

    @Override
    public void sendToPlayer(Message msg, ServerPlayerEntity e) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        msg.encode(buf);
        ServerPlayNetworking.send(e, getMessageIdentifier(msg), buf);
    }

    // Fabric's APIs are not side-agnostic.
    // We punt this to a separate class file to keep it from being eager-loaded on a server environment.
    private static final class ClientProxy {
        private ClientProxy() {
            throw new RuntimeException("new ClientProxy()");
        }

        public static <T extends Message> void register(Identifier id, Class<T> msg, java.util.function.Function<PacketByteBuf, T> decoder) {
            ClientPlayNetworking.registerGlobalReceiver(id, (client, ignore1, buffer, ignore2) -> {
                Message m = Message.decode(buffer, msg, decoder);
                client.execute(m::receive);
            });
        }
    }
}
