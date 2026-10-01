package org.gameshakers.servidor;

import org.gameshakers.comun.BufferPcm;
import org.gameshakers.comun.Protocolo;

import javax.sound.sampled.*;

/** Captura el micrófono del locutor (44.1 kHz, mono, 16 bits). */
public class FuenteMicrofono {

    private static final int MAX_RETRASO = (int) (Protocolo.FRECUENCIA * 0.25);   // 250 ms

    private final BufferPcm buffer = new BufferPcm((int) Protocolo.FRECUENCIA);
    private TargetDataLine linea;
    private Thread hilo;
    private volatile boolean activo;

    public void abrir() throws LineUnavailableException {
        AudioFormat formato = Protocolo.formatoPcm(1);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, formato);
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("No se encontró un micrófono compatible (44.1 kHz, 16 bits, mono).");
        }
        linea = (TargetDataLine) AudioSystem.getLine(info);
        linea.open(formato, Protocolo.MUESTRAS_POR_TRAMA * 2 * 4);
        linea.start();
        buffer.vaciar();
        activo = true;
        hilo = new Thread(this::capturar, "microfono");
        hilo.setDaemon(true);
        hilo.start();
    }

    public void cerrar() {
        activo = false;
        if (linea != null) {
            linea.stop();
            linea.close();
        }
        buffer.vaciar();
    }

    public boolean activo() {
        return activo;
    }

    /** Copia hasta {@code n} muestras mono; devuelve cuántas había. */
    int leer(short[] dst, int n) {
        // si el micrófono va más rápido que la transmisión se descarta lo viejo para no acumular retraso
        buffer.recortar(MAX_RETRASO);
        return buffer.leer(dst, n);
    }

    private void capturar() {
        byte[] bytes = new byte[Protocolo.MUESTRAS_POR_TRAMA * 2];
        short[] muestras = new short[Protocolo.MUESTRAS_POR_TRAMA];
        while (activo) {
            int n = linea.read(bytes, 0, bytes.length);
            int cuadros = n / 2;
            for (int i = 0; i < cuadros; i++) {
                muestras[i] = (short) ((bytes[2 * i] & 0xFF) | (bytes[2 * i + 1] << 8));
            }
            buffer.escribirDescartando(muestras, cuadros);
        }
    }
}
