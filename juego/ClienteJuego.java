package juego;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

/**
 * Lee los comandos del usuario y conserva el secreto hasta recibir el intento.
 */
public final class ClienteJuego {
    private static final int PUERTO_PREDETERMINADO = 5000;
    private static final int TIEMPO_CONEXION_MS = 5_000;
    private static final int TIEMPO_RESPUESTA_MS = 10_000;

    private final String host;
    private final int puerto;
    private int minimo;
    private int maximo;
    private boolean partidaPerdida;

    public ClienteJuego(String host, int puerto) {
        this.host = Objects.requireNonNull(host, "La dirección del servidor es obligatoria.");
        if (host.isBlank() || puerto < 1 || puerto > 65535) {
            throw new IllegalArgumentException("Indica una dirección y un puerto entre 1 y 65535.");
        }
        this.puerto = puerto;
    }

    public static void main(String[] args) {
        try {
            if (args.length > 2) {
                throw new IllegalArgumentException("Uso: java juego.ClienteJuego [host] [puerto]");
            }
            String host = args.length >= 1 ? args[0] : "127.0.0.1";
            int puerto = args.length == 2 ? Integer.parseInt(args[1]) : PUERTO_PREDETERMINADO;
            new ClienteJuego(host, puerto).iniciar();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            System.err.println("Se interrumpió la generación del número. La conexión se cerró.");
            System.exit(1);
        } catch (IOException | IllegalArgumentException error) {
            System.err.println("No se pudo completar la partida: " + error.getMessage());
            System.exit(1);
        }
    }

    public void iniciar() throws IOException, InterruptedException {
        // System.in pertenece al proceso; no se cierra al finalizar esta conexión.
        BufferedReader consola = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, puerto), TIEMPO_CONEXION_MS);
            try (CanalComunicacion canal = new CanalComunicacion(socket)) {
                canal.configurarTiempoEspera(TIEMPO_RESPUESTA_MS);
                recibirRango(canal);
                System.out.printf("Conectado. Números entre %d y %d.%n", minimo, maximo);
                System.out.println("Escribe jugar para una ronda o terminar para salir.");
                boolean continuar = true;
                while (continuar) {
                    System.out.print(partidaPerdida ? "[terminar] > " : "[jugar / terminar] > ");
                    System.out.flush();
                    String entrada = consola.readLine();
                    String comando = entrada == null ? "terminar" : entrada.trim().toLowerCase(Locale.ROOT);
                    switch (comando) {
                        case "terminar" -> {
                            terminarJuego(canal);
                            continuar = false;
                        }
                        case "jugar" -> {
                            if (partidaPerdida) {
                                System.out.println("La partida ya terminó. Escribe terminar para ver el resumen.");
                            } else {
                                jugarRonda(canal);
                            }
                        }
                        default -> System.out.println("Comando no reconocido. Usa jugar o terminar.");
                    }
                }
            }
        }
    }

    private void recibirRango(CanalComunicacion canal) throws IOException {
        String mensaje = canal.recibir();
        String[] partes = mensaje.split(":", -1);
        try {
            if (partes.length != 3 || !partes[0].equals("RANGO")) {
                throw new IllegalArgumentException();
            }
            minimo = Integer.parseInt(partes[1]);
            maximo = Integer.parseInt(partes[2]);
            GeneradorNumero.validarRango(minimo, maximo);
        } catch (IllegalArgumentException error) {
            throw new IOException("El servidor envió un rango inválido.", error);
        }
    }

    private void jugarRonda(CanalComunicacion canal) throws IOException, InterruptedException {
        int numero = generarNumeroEnHilo();
        canal.enviar("JUGAR");
        String respuesta = canal.recibir();
        if (respuesta.equals("ERROR:PARTIDA_PERDIDA")) {
            partidaPerdida = true;
            System.out.println("Perdiste. Escribe terminar para ver el resumen.");
            return;
        }

        int intento;
        try {
            if (!respuesta.startsWith("INTENTO:")) {
                throw new NumberFormatException();
            }
            intento = Integer.parseInt(respuesta.substring("INTENTO:".length()));
            if (intento < minimo || intento > maximo) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException error) {
            throw new IOException("El servidor envió un intento inválido: " + respuesta, error);
        }

        canal.enviar("NUMERO:" + numero);
        String resultado = canal.recibir();
        System.out.printf("Tu número: %d | Intento del servidor: %d%n", numero, intento);
        switch (resultado) {
            case "ACIERTO" -> System.out.println("El servidor acertó.");
            case "DESACIERTO" -> System.out.println("El servidor falló.");
            case "PERDISTE" -> {
                partidaPerdida = true;
                System.out.println("Perdiste: el servidor acumuló tres desaciertos seguidos.");
                System.out.println("Escribe terminar para cerrar y ver el resumen.");
            }
            default -> throw new IOException("Respuesta de ronda inesperada: " + resultado);
        }
    }

    private int generarNumeroEnHilo() throws InterruptedException {
        GeneradorNumero tarea = new GeneradorNumero(minimo, maximo);
        Thread hilo = new Thread(tarea, "generador-numero");
        hilo.start();
        // join espera la tarea y permite leer el resultado que escribió el otro hilo.
        hilo.join();
        return tarea.getNumero();
    }

    private void terminarJuego(CanalComunicacion canal) throws IOException {
        canal.enviar("terminar");
        String mensaje = canal.recibir();
        String[] partes = mensaje.split(":", -1);
        try {
            if (partes.length != 3 || !partes[0].equals("RESUMEN")) {
                throw new NumberFormatException();
            }
            int aciertos = Integer.parseInt(partes[1]);
            int desaciertos = Integer.parseInt(partes[2]);
            if (aciertos < 0 || desaciertos < 0) {
                throw new NumberFormatException();
            }
            System.out.printf("Resumen del servidor: %d aciertos y %d desaciertos.%n", aciertos, desaciertos);
            System.out.println("Conexión finalizada.");
        } catch (NumberFormatException error) {
            throw new IOException("El servidor envió un resumen inválido.", error);
        }
    }
}
