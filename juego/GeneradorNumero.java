package juego;

import java.util.concurrent.ThreadLocalRandom;

/** Tarea del cliente que genera el número secreto de una ronda. */
public final class GeneradorNumero implements Runnable {
    private final int minimo;
    private final int maximo;
    private Integer numero;

    public GeneradorNumero(int minimo, int maximo) {
        validarRango(minimo, maximo);
        this.minimo = minimo;
        this.maximo = maximo;
    }

    @Override
    public void run() {
        numero = (int) ThreadLocalRandom.current().nextLong(minimo, (long) maximo + 1);
    }

    /** Se consulta después de join(), cuando la tarea ya terminó. */
    public int getNumero() {
        if (numero == null) {
            throw new IllegalStateException("El número todavía no se ha generado.");
        }
        return numero;
    }

    static void validarRango(int minimo, int maximo) {
        if (minimo > maximo) {
            throw new IllegalArgumentException("El mínimo no puede superar al máximo.");
        }
    }
}
