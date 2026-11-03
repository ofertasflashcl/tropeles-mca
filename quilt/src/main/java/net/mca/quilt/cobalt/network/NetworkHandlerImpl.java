package net.mca.quilt.cobalt.network;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.mca.MCA;
import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.quiltmc.loader.api.minecraft.MinecraftQuiltLoader;
import org.quiltmc.qsl.networking.api.ServerPlayNetworking;
import org.quiltmc.qsl.networking.api.client.ClientPlayNetworking;

import java.util.Locale;

public class NetworkHandlerImpl extends NetworkHandler.Impl {
    @Override
    public <T extends Message> void registerMessage(Class<T> msg, java.util.function.Function<net.minecraft.network.PacketByteBuf, T> decoder, boolean toServer) {
        Identifier id = new Identifier(MCA.MOD_ID, msg.getName().toLowerCase(Locale.ENGLISH));

        if (toServer) ServerPlayNetworking.registerGlobalReceiver(id, (server, player, handler, buffer, responder) -> {
            Message m = Message.decode(buffer, msg, decoder);
            server.execute(() -> m.receive(player));
        });

        if (!toServer && MinecraftQuiltLoader.getEnvironmentType() == EnvType.CLIENT) {
            ClientProxy.register(id, msg, decoder);
        }
    }

    @Override
    public void sendToServer(Message m) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        m.encode(buf);
        ClientPlayNetworking.send(new Identifier(MCA.MOD_ID, m.getClass().getName().toLowerCase(Locale.ENGLISH)), buf);
    }

    @Override
    public void sendToPlayer(Message m, ServerPlayerEntity e) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        m.encode(buf);
        ServerPlayNetworking.send(e, new Identifier(MCA.MOD_ID, m.getClass().getName().toLowerCase(Locale.ENGLISH)), buf);
    }

    // Fabric's APIs are not side-agnostic.
    // We punt this to a separate class file to keep it from being eager-loaded on a server environment.
    private static final class ClientProxy {
        private ClientProxy() {throw new RuntimeException("new ClientProxy()");}
        public static <T extends Message> void register(Identifier id, Class<T> msg, java.util.function.Function<PacketByteBuf, T> decoder) {
            ClientPlayNetworking.registerGlobalReceiver(id, (client, ignore1, buffer, ignore2) -> {
                Message m = Message.decode(buffer, msg, decoder);
                client.execute(m::receive);
            });
        }
    }
}
