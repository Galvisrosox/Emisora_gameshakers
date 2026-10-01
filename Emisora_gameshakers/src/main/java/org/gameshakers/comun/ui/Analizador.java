package org.gameshakers.comun.ui;

/**
 * Calcula el nivel RMS y la energía de algunas bandas (algoritmo de Goertzel)
 * a partir de un bloque de muestras estéreo intercaladas.
 */
public final class Analizador {

    public static final double[] FRECUENCIAS = {60, 120, 250, 500, 1000, 2000, 3500, 6000, 9000, 13000};

    public static float rms(short[] muestras, int n) {
        if (n <= 0) return 0f;
        double suma = 0;
        for (int i = 0; i < n; i++) {
            double v = muestras[i] / 32768.0;
            suma += v * v;
        }
        return (float) Math.sqrt(suma / n);
    }

    /** {@code muestras} intercaladas con {@code canales} canales; devuelve un valor 0..1 por banda. */
    public static float[] bandas(short[] muestras, int n, int canales, float frecuenciaMuestreo) {
        int cuadros = n / canales;
        float[] res = new float[FRECUENCIAS.length];
        if (cuadros < 64) return res;
        for (int b = 0; b < FRECUENCIAS.length; b++) {
            double coef = 2 * Math.cos(2 * Math.PI * FRECUENCIAS[b] / frecuenciaMuestreo);
            double s1 = 0, s2 = 0;
            for (int i = 0; i < cuadros; i++) {
                double x = 0;
                for (int c = 0; c < canales; c++) x += muestras[i * canales + c];
                x /= canales * 32768.0;
                // ventana de Hann para que las bandas no se mezclen tanto
                x *= 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (cuadros - 1));
                double s = x + coef * s1 - s2;
                s2 = s1;
                s1 = s;
            }
            double potencia = s1 * s1 + s2 * s2 - coef * s1 * s2;
            double magnitud = Math.sqrt(Math.max(potencia, 0)) / (cuadros / 4.0);
            double db = 20 * Math.log10(magnitud + 1e-9);
            res[b] = (float) Math.max(0, Math.min(1, (db + 60) / 60));
        }
        return res;
    }

    private Analizador() {
    }
}
