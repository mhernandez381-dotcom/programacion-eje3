package juego;

/** Conserva los resultados de una partida; solo el servidor los modifica. */
public final class EstadisticasJuego {
    private static final int LIMITE_DESACIERTOS = 3;

    private int aciertos;
    private int desaciertos;
    private int desaciertosConsecutivos;

    public void registrarResultado(boolean acierto) {
        if (haPerdido()) {
            throw new IllegalStateException("La partida ya terminó por tres desaciertos seguidos.");
        }
        if (acierto) {
            aciertos++;
            // Un acierto corta la racha, pero conserva los desaciertos acumulados.
            desaciertosConsecutivos = 0;
        } else {
            desaciertos++;
            desaciertosConsecutivos++;
        }
    }

    public boolean haPerdido() {
        return desaciertosConsecutivos >= LIMITE_DESACIERTOS;
    }

    public int getAciertos() {
        return aciertos;
    }

    public int getDesaciertos() {
        return desaciertos;
    }

    public int getDesaciertosConsecutivos() {
        return desaciertosConsecutivos;
    }

    public String obtenerResumen() {
        return "RESUMEN:" + aciertos + ":" + desaciertos;
    }
}
