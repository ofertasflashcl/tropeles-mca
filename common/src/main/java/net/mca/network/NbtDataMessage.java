package net.mca.network;

import net.mca.cobalt.network.Message;
import net.minecraft.nbt.NbtCompound;

public abstract class NbtDataMessage implements Message {
    private final NbtCompound data;
    public NbtDataMessage(NbtCompound data) { this.data = data; }
    public NbtCompound getData() { return data; }
}
