package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.cobalt.network.Message;
import net.mca.resources.data.analysis.Analysis;


public class AnalysisResults implements Message {

    public final Analysis<?> analysis;

    public AnalysisResults(Analysis<?> analysis) {
        this.analysis = analysis;
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleSkinListResponse(this);
    }

    private AnalysisResults(net.minecraft.network.PacketByteBuf buffer) {
        this.analysis = net.mca.cobalt.network.PacketCodecs.analysis(buffer);
    }

    public static AnalysisResults decode(net.minecraft.network.PacketByteBuf buffer) { return new AnalysisResults(buffer); }

    @Override
    public void encode(net.minecraft.network.PacketByteBuf buffer) {
        buffer.writeVarInt(net.mca.cobalt.network.PacketCodecs.VERSION);
        net.mca.cobalt.network.PacketCodecs.analysis(buffer, this.analysis);
        if (buffer.readableBytes() > net.mca.cobalt.network.PacketCodecs.MAX_PACKET) throw new io.netty.handler.codec.EncoderException("MCA packet too large");
    }
}
