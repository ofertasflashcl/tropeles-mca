package net.mca.entity.ai.chatAI;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** A chat/health response is bounded in bytes before UTF-8 decoding, including error bodies and chunked streams. */
final class RespuestaAcotada {
    static final int MAX_BYTES = 1024 * 1024;
    private RespuestaAcotada() { }

    static String leer(InputStream input) throws IOException {
        if (input == null) return "";
        try (InputStream stream = input) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
            byte[] buffer = new byte[8192];
            int n;
            while ((n = stream.read(buffer)) != -1) {
                if (n > MAX_BYTES - out.size()) throw new IOException("Respuesta de IA supera " + MAX_BYTES + " bytes");
                out.write(buffer, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }
}
