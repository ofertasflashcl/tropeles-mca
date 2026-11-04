package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.cobalt.network.Message;

/**
 * The see-through plan of a building under way (every cell: where, the exact block state by its raw id, and its
 * kind), or word that it is gone, for the players near it to see the whole space it will take.
 */
public class FantasmaMessage implements Message {

    public final String id;
    public final String dim;
    public final boolean borrar;
    public final String quien;
    public final int[] xs;
    public final int[] ys;
    public final int[] zs;
    public final int[] estados;
    public final byte[] roles;

    public FantasmaMessage(String id, String dim, String quien, int[] xs, int[] ys, int[] zs, int[] estados, byte[] roles) {
        this.id = id;
        this.dim = dim;
        this.borrar = false;
        this.quien = quien;
        this.xs = xs;
        this.ys = ys;
        this.zs = zs;
        this.estados = estados;
        this.roles = roles;
    }

    private FantasmaMessage(String id, String dim) {
        this.id = id;
        this.dim = dim;
        this.borrar = true;
        this.quien = "";
        this.xs = new int[0];
        this.ys = new int[0];
        this.zs = new int[0];
        this.estados = new int[0];
        this.roles = new byte[0];
    }

    public static FantasmaMessage borrar(String id, String dim) {
        return new FantasmaMessage(id, dim);
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleFantasma(this);
    }

    private FantasmaMessage(net.minecraft.network.PacketByteBuf buffer) {
        this.id = net.mca.cobalt.network.PacketCodecs.string(buffer);
        this.dim = net.mca.cobalt.network.PacketCodecs.string(buffer);
        this.borrar = buffer.readBoolean();
        this.quien = net.mca.cobalt.network.PacketCodecs.string(buffer);
        this.xs = net.mca.cobalt.network.PacketCodecs.ints(buffer);
        this.ys = net.mca.cobalt.network.PacketCodecs.ints(buffer);
        this.zs = net.mca.cobalt.network.PacketCodecs.ints(buffer);
        this.estados = net.mca.cobalt.network.PacketCodecs.ints(buffer);
        this.roles = net.mca.cobalt.network.PacketCodecs.bytes(buffer);
        if (xs.length != ys.length || xs.length != zs.length || xs.length != estados.length || xs.length != roles.length) throw new io.netty.handler.codec.DecoderException("Mismatched ghost arrays");
    }

    public static FantasmaMessage decode(net.minecraft.network.PacketByteBuf buffer) { return new FantasmaMessage(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        net.mca.cobalt.network.PacketCodecs.string(buffer, this.id);
        net.mca.cobalt.network.PacketCodecs.string(buffer, this.dim);
        buffer.writeBoolean(this.borrar);
        net.mca.cobalt.network.PacketCodecs.string(buffer, this.quien);
        net.mca.cobalt.network.PacketCodecs.ints(buffer, this.xs);
        net.mca.cobalt.network.PacketCodecs.ints(buffer, this.ys);
        net.mca.cobalt.network.PacketCodecs.ints(buffer, this.zs);
        net.mca.cobalt.network.PacketCodecs.ints(buffer, this.estados);
        net.mca.cobalt.network.PacketCodecs.bytes(buffer, this.roles);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
