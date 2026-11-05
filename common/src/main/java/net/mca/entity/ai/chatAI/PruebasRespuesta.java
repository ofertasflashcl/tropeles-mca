package net.mca.entity.ai.chatAI;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Boundary and cleanup checks against the production reader; no network or duplicated parser. */
public final class PruebasRespuesta {
    private PruebasRespuesta() { }
    private static final class Tracked extends ByteArrayInputStream {
        boolean closed;
        Tracked(byte[] data) { super(data); }
        @Override public void close() throws IOException { closed = true; super.close(); }
    }
    public static String run() throws IOException {
        String valid = "á, madera y memoria";
        Tracked text = new Tracked(valid.getBytes(StandardCharsets.UTF_8));
        if (!valid.equals(RespuestaAcotada.leer(text)) || !text.closed) throw new IllegalStateException("UTF-8 o cierre incorrecto");
        Tracked exact = new Tracked(new byte[RespuestaAcotada.MAX_BYTES]);
        if (RespuestaAcotada.leer(exact).length() != RespuestaAcotada.MAX_BYTES || !exact.closed) throw new IllegalStateException("Rechaza el limite exacto");
        Tracked excess = new Tracked(new byte[RespuestaAcotada.MAX_BYTES + 1]);
        boolean refused = false;
        try { RespuestaAcotada.leer(excess); } catch (IOException expected) { refused = true; }
        if (!refused || !excess.closed || !RespuestaAcotada.leer(null).isEmpty()) throw new IllegalStateException("Respuesta ilimitada o recurso sin cerrar");
        return "UTF-8, 1 MiB exacto, exceso rechazado y streams cerrados en exito/fallo";
    }
}
