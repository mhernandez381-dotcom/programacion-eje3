package juego;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;

/** Atiende una partida local y registra los intentos del servidor. */
public final class ServidorJuego {
    private static final int PUERTO_PREDETERMINADO = 5000;
    private static final int TIEMPO_RONDA_MS = 5_000;

    private final int puerto;
    private final int minimo;
    private final int maximo;
    private final IntSupplier intentos;
    private final EstadisticasJuego estadisticas = new EstadisticasJuego();

    public ServidorJuego(int puerto, int minimo, int maximo) {
        this(puerto, minimo, maximo,
                () -> (int) ThreadLocalRandom.current().nextLong(minimo, (long) maximo + 1));
    }

    // Las pruebas sustituyen el azar sin cambiar el protocolo ni las reglas.
    ServidorJuego(int puerto, int minimo, int maximo, IntSupplier intentos) {
        if (puerto < 1 || puerto > 65535) {
            throw new IllegalArgumentException("El puerto debe estar entre 1 y 65535.");
        }
        GeneradorNumero.validarRango(minimo, maximo);
        this.puerto = puerto;
        this.minimo = minimo;
        this.maximo = maximo;
        this.intentos = Objects.requireNonNull(intentos, "El generador de intentos es obligatorio.");
    }

    public static void main(String[] args) {
        try {
            if (args.length > 1) {
                throw new IllegalArgumentException("Uso: java juego.ServidorJuego [puerto]");
            }
            int puerto = args.length == 1 ? Integer.parseInt(args[0]) : PUERTO_PREDETERMINADO;
            new ServidorJuego(puerto, 1, 5).iniciar();
        } catch (IOException | IllegalArgumentException error) {
            System.err.println("No se pudo completar la partida: " + error.getMessage());
            System.exit(1);
        }
    }

    public void iniciar() throws IOException {
        InetAddress direccion = InetAddress.getByName("127.0.0.1");
        try (ServerSocket servidor = new ServerSocket(puerto, 1, direccion)) {
            System.out.printf("Servidor listo en 127.0.0.1:%d. Esperando al cliente...%n", puerto);
            try (CanalComunicacion canal = new CanalComunicacion(servidor.accept())) {
                System.out.println("Cliente conectado.");
                try {
                    atenderCliente(canal);
                } catch (SocketTimeoutException error) {
                    canal.enviar("ERROR:TIEMPO_AGOTADO");
                    System.out.println("La ronda quedó incompleta: el cliente no respondió a tiempo.");
                } catch (EOFException error) {
                    System.out.println("El cliente se desconectó. Se conservan las rondas completas.");
                } finally {
                    System.out.printf("Resumen del servidor: %d aciertos y %d desaciertos.%n",
                            estadisticas.getAciertos(), estadisticas.getDesaciertos());
                }
            }
        }
    }

    private void atenderCliente(CanalComunicacion canal) throws IOException {
        canal.enviar("RANGO:" + minimo + ":" + maximo);
        boolean continuar = true;
        while (continuar) {
            canal.configurarTiempoEspera(0);
            String comando = canal.recibir();
            switch (comando) {
                case "terminar" -> {
                    enviarResumen(canal);
                    continuar = false;
                }
                case "JUGAR" -> {
                    if (estadisticas.haPerdido()) {
                        canal.enviar("ERROR:PARTIDA_PERDIDA");
                    } else {
                        continuar = procesarRonda(canal);
                    }
                }
                default -> canal.enviar("ERROR:COMANDO_INVALIDO");
            }
        }
    }

    private boolean procesarRonda(CanalComunicacion canal) throws IOException {
        int intento = generarIntento();
        canal.configurarTiempoEspera(TIEMPO_RONDA_MS);
        // El intento sale antes de recibir el secreto; no depende del número revelado.
        canal.enviar("INTENTO:" + intento);
        String mensaje = canal.recibir();
        if (mensaje.equals("terminar")) {
            enviarResumen(canal);
            return false;
        }

        int numero;
        try {
            if (!mensaje.startsWith("NUMERO:")) {
                throw new NumberFormatException();
            }
            numero = Integer.parseInt(mensaje.substring("NUMERO:".length()));
            if (numero < minimo || numero > maximo) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException error) {
            canal.enviar("ERROR:NUMERO_INVALIDO");
            return true;
        }

        boolean acierto = intento == numero;
        estadisticas.registrarResultado(acierto);
        String resultado = estadisticas.haPerdido() ? "PERDISTE" : acierto ? "ACIERTO" : "DESACIERTO";
        System.out.printf("Cliente: %d | Intento: %d | %s | Fallos seguidos: %d%n",
                numero, intento, acierto ? "Acierto" : "Desacierto",
                estadisticas.getDesaciertosConsecutivos());
        if (estadisticas.haPerdido()) {
            System.out.println("Perdiste. Esperando que el cliente envíe terminar.");
        }
        canal.enviar(resultado);
        return true;
    }

    private int generarIntento() {
        int intento = intentos.getAsInt();
        if (intento < minimo || intento > maximo) {
            throw new IllegalStateException("El intento está fuera del rango de la partida.");
        }
        return intento;
    }

    private void enviarResumen(CanalComunicacion canal) throws IOException {
        canal.enviar(estadisticas.obtenerResumen());
    }
}
