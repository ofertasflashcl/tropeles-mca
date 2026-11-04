package net.mca.cobalt.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.function.Function;

public interface Message {
    static <T extends Message> T decode(PacketByteBuf b, Class<T> expected, Function<PacketByteBuf, T> codec) {
        if (b.readableBytes() > PacketCodecs.MAX_PACKET || b.readVarInt() != PacketCodecs.VERSION)
            throw new DecoderException("Unsupported/oversized MCA packet");
        T result = codec.apply(b);
        if (result == null || result.getClass() != expected || b.isReadable()) throw new DecoderException("Invalid MCA packet framing");
        return result;
    }
    void encode(PacketByteBuf b);
    default void receive() { }
    default void receive(ServerPlayerEntity player) { }
}
