package org.gameshakers.comun;

/**
 * Buffer circular de muestras de 16 bits.
 * Quien escribe espera si está lleno; quien lee nunca espera (toma lo que haya).
 */
public class BufferPcm {

    private final short[] datos;
    private int inicio = 0;
    private int cantidad = 0;

    public BufferPcm(int capacidad) {
        datos = new short[capacidad];
    }

    public synchronized void escribir(short[] src, int n) throws InterruptedException {
        int i = 0;
        while (i < n) {
            while (cantidad == datos.length) wait();
            int fin = (inicio + cantidad) % datos.length;
            int libre = Math.min(datos.length - cantidad, datos.length - fin);
            int k = Math.min(libre, n - i);
            System.arraycopy(src, i, datos, fin, k);
            cantidad += k;
            i += k;
        }
    }

    /** Escribe sin esperar; si no cabe, descarta lo más antiguo. */
    public synchronized void escribirDescartando(short[] src, int n) {
        for (int i = 0; i < n; i++) {
            if (cantidad == datos.length) {
                inicio = (inicio + 1) % datos.length;
                cantidad--;
            }
            datos[(inicio + cantidad) % datos.length] = src[i];
            cantidad++;
        }
    }

    /** Copia hasta {@code n} muestras en {@code dst}; devuelve cuántas copió. */
    public synchronized int leer(short[] dst, int n) {
        int k = Math.min(n, cantidad);
        for (int i = 0; i < k; i++) {
            dst[i] = datos[inicio];
            inicio = (inicio + 1) % datos.length;
        }
        cantidad -= k;
        if (k > 0) notifyAll();
        return k;
    }

    /** Descarta muestras antiguas hasta dejar como máximo {@code max}. */
    public synchronized void recortar(int max) {
        if (cantidad > max) {
            int sobra = cantidad - max;
            inicio = (inicio + sobra) % datos.length;
            cantidad = max;
            notifyAll();
        }
    }

    public synchronized int disponibles() {
        return cantidad;
    }

    public synchronized void vaciar() {
        inicio = 0;
        cantidad = 0;
        notifyAll();
    }
}
