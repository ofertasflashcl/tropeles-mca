package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.cobalt.network.Message;

/**
 * The see-through plan of a building under way (every cell: where, the exact block state by its raw id, and its
 * kind), or word that it is gone, for the players near it to see the whole space it will take.
 */
public class FantasmaMessage implements Message {
    private static final long serialVersionUID = 6519470238651940211L;

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
}
