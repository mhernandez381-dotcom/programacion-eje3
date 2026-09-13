package juego;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Objects;

/** Agrupa los flujos de una conexión y se hace cargo de cerrar su socket. */
public final class CanalComunicacion implements AutoCloseable {
    private final Socket socket;
    private final DataInputStream entrada;
    private final DataOutputStream salida;

    public CanalComunicacion(Socket socket) throws IOException {
        this.socket = Objects.requireNonNull(socket, "El socket es obligatorio.");
        try {
            entrada = new DataInputStream(socket.getInputStream());
            salida = new DataOutputStream(socket.getOutputStream());
        } catch (IOException error) {
            try {
                socket.close();
            } catch (IOException errorCierre) {
                error.addSuppressed(errorCierre);
            }
            throw error;
        }
    }

    public void enviar(String mensaje) throws IOException {
        salida.writeUTF(mensaje);
        salida.flush();
    }

    public String recibir() throws IOException {
        return entrada.readUTF();
    }

    /**
     * Cero permite esperar al usuario; un valor positivo limita una lectura de red.
     */
    public void configurarTiempoEspera(int milisegundos) throws IOException {
        socket.setSoTimeout(milisegundos);
    }

    @Override
    public void close() throws IOException {
        // Al cerrar el socket también se cierran sus flujos de entrada y salida.
        socket.close();
    }
}
