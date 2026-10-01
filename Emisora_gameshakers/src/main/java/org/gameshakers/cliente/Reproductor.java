package org.gameshakers.cliente;

import javazoom.jl.decoder.*;
import org.gameshakers.comun.Protocolo;
import org.gameshakers.comun.ui.Analizador;

import javax.sound.sampled.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * Recibe los bloques MP3 que llegan por UDP, los guarda en un pequeño buffer
 * (para absorber las variaciones de la red), los decodifica y los reproduce.
 *
 * Todos los oyentes usan el mismo buffer, así que escuchan prácticamente
 * al mismo tiempo.
 */
public class Reproductor {

    /** Paquetes que se acumulan antes de empezar a sonar (~2 tramas de 26 ms cada uno ≈ 420 ms). */
    private static final int PRECARGA = 8;
    /** Si el buffer pasa de esto (≈ 2 s), se descarta lo viejo para no quedarse atrasado. */
    private static final int MAX_EN_COLA = 40;

    private final LinkedBlockingDeque<byte[]> cola = new LinkedBlockingDeque<>();
    private final Thread hilo;
    private volatile boolean activo = true;
    private volatile boolean cargando = true;
    private volatile float volumen = 0.9f;
    private volatile boolean silencio = false;

    private volatile float nivel;
    private volatile float[] bandas = new float[Analizador.FRECUENCIAS.length];
    private volatile String error;
    private volatile long tramasDecodificadas = 0;

    public Reproductor() {
        hilo = new Thread(this::reproducir, "reproductor");
        hilo.setDaemon(true);
        hilo.start();
    }

    /** Agrega un bloque de tramas MP3 recibido. */
    public void recibir(byte[] mp3) {
        cola.offerLast(mp3);
        if (cola.size() > MAX_EN_COLA) {
            while (cola.size() > PRECARGA) cola.pollFirst();
        }
    }

    public void detener() {
        activo = false;
        hilo.interrupt();
        cola.clear();
    }

    public void setVolumen(float v) {
        volumen = v;
    }

    public void setSilencio(boolean s) {
        silencio = s;
    }

    public boolean cargando() {
        return cargando;
    }

    /** Milisegundos de audio esperando en el buffer. */
    public int bufferMs() {
        return (int) (cola.size() * Protocolo.TRAMAS_POR_PAQUETE * Protocolo.MUESTRAS_POR_TRAMA * 1000 / Protocolo.FRECUENCIA);
    }

    public float nivel() {
        return nivel;
    }

    public float[] bandas() {
        return bandas;
    }

    public String error() {
        return error;
    }

    public long tramasDecodificadas() {
        return tramasDecodificadas;
    }

    private void reproducir() {
        SourceDataLine linea = null;
        boolean sinSalida = false;
        byte[] pcm = new byte[0];
        long relojSinSalida = 0;
        while (activo) {
            try {
                Bitstream bits = new Bitstream(new EntradaCola());
                Decoder decoder = new Decoder();
                Header h;
                while (activo && (h = bits.readFrame()) != null) {
                    SampleBuffer sb = (SampleBuffer) decoder.decodeFrame(h, bits);
                    bits.closeFrame();
                    tramasDecodificadas++;
                    short[] m = sb.getBuffer();
                    int n = sb.getBufferLength();
                    int canales = sb.getChannelCount();

                    nivel = Analizador.rms(m, n);
                    bandas = Analizador.bandas(m, n, canales, sb.getSampleFrequency());

                    if (linea == null && !sinSalida) {
                        AudioFormat f = new AudioFormat(sb.getSampleFrequency(), 16, canales, true, false);
                        try {
                            linea = AudioSystem.getSourceDataLine(f);
                            linea.open(f, (int) (f.getFrameRate() * f.getFrameSize() / 4));   // 250 ms
                            linea.start();
                        } catch (LineUnavailableException | IllegalArgumentException e) {
                            error = "Este equipo no tiene salida de audio disponible: " + e.getMessage();
                            sinSalida = true;
                            linea = null;
                        }
                    }
                    if (linea != null) {
                        if (pcm.length < n * 2) pcm = new byte[n * 2];
                        float g = silencio ? 0f : volumen;
                        for (int i = 0; i < n; i++) {
                            int s = Math.max(-32768, Math.min(32767, (int) (m[i] * g)));
                            pcm[2 * i] = (byte) s;
                            pcm[2 * i + 1] = (byte) (s >> 8);
                        }
                        linea.write(pcm, 0, n * 2);
                    } else if (sinSalida) {
                        // sin parlantes se sigue decodificando al ritmo real para no vaciar el buffer de golpe
                        long ahora = System.nanoTime();
                        if (relojSinSalida == 0 || ahora - relojSinSalida > 500_000_000L) relojSinSalida = ahora;
                        relojSinSalida += (long) (n / (double) canales * 1e9 / sb.getSampleFrequency());
                        long espera = relojSinSalida - ahora;
                        if (espera > 0) Thread.sleep(espera / 1_000_000, (int) (espera % 1_000_000));
                    }
                }
            } catch (BitstreamException | DecoderException e) {
                // tramas dañadas o perdidas: se reinicia el decodificador y se sigue
            } catch (InterruptedException e) {
                break;
            } catch (RuntimeException e) {
                // JLayer puede fallar con datos incompletos; se reinicia el decodificador
            }
        }
        if (linea != null) {
            linea.stop();
            linea.close();
        }
        nivel = 0;
        bandas = new float[Analizador.FRECUENCIAS.length];
    }

    /**
     * Presenta la cola de paquetes como un flujo continuo de bytes para JLayer.
     * Si la cola se vacía, espera a tener la precarga completa antes de continuar.
     */
    private class EntradaCola extends InputStream {
        private byte[] actual;
        private int pos;

        private boolean asegurarDatos() {
            while (actual == null || pos >= actual.length) {
                if (!activo) return false;
                if (cola.isEmpty()) cargando = true;
                if (cargando) {
                    if (cola.size() < PRECARGA) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            return false;
                        }
                        continue;
                    }
                    cargando = false;
                }
                try {
                    actual = cola.pollFirst(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    return false;
                }
                pos = 0;
            }
            return true;
        }

        @Override
        public int read() {
            if (!asegurarDatos()) return -1;
            return actual[pos++] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) return 0;
            if (!asegurarDatos()) return -1;
            int k = Math.min(len, actual.length - pos);
            System.arraycopy(actual, pos, b, off, k);
            pos += k;
            return k;
        }
    }
}
