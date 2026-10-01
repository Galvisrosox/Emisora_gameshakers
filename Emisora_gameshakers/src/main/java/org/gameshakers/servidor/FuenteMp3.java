package org.gameshakers.servidor;

import javazoom.jl.decoder.*;
import org.gameshakers.comun.BufferPcm;
import org.gameshakers.comun.Protocolo;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Decodifica un archivo MP3 a PCM 44.1 kHz estéreo y lo deja en un buffer
 * del que el mezclador va tomando al ritmo de la transmisión.
 */
public class FuenteMp3 {

    private final File archivo;
    private final BufferPcm buffer = new BufferPcm((int) Protocolo.FRECUENCIA * 2 * 2); // 2 s estéreo
    private final Thread hilo;
    private volatile boolean terminoDecodificar = false;
    private volatile boolean cancelada = false;
    private volatile String error;
    private volatile long muestrasEntregadas = 0;    // cuadros estéreo ya enviados al mezclador
    private final long duracionMs;

    public FuenteMp3(File archivo) {
        this.archivo = archivo;
        this.duracionMs = medirDuracion(archivo);
        hilo = new Thread(this::decodificar, "mp3-" + archivo.getName());
        hilo.setDaemon(true);
        hilo.start();
    }

    public String titulo() {
        return titulo(archivo);
    }

    public static String titulo(File f) {
        String n = f.getName();
        return n.toLowerCase().endsWith(".mp3") ? n.substring(0, n.length() - 4) : n;
    }

    /** Copia hasta {@code n} muestras intercaladas; devuelve cuántas había. */
    int leer(short[] dst, int n) {
        int k = buffer.leer(dst, n);
        muestrasEntregadas += k / 2;
        return k;
    }

    boolean termino() {
        return (terminoDecodificar || cancelada) && buffer.disponibles() == 0;
    }

    public String error() {
        return error;
    }

    public long posicionMs() {
        return (long) (muestrasEntregadas * 1000 / Protocolo.FRECUENCIA);
    }

    public long duracionMs() {
        return duracionMs;
    }

    public void cancelar() {
        cancelada = true;
        hilo.interrupt();
        buffer.vaciar();
    }

    private void decodificar() {
        try (InputStream in = new BufferedInputStream(new FileInputStream(archivo))) {
            Bitstream bits = new Bitstream(in);
            Decoder decoder = new Decoder();
            Remuestreador remuestreo = null;
            Header h;
            while (!cancelada && (h = bits.readFrame()) != null) {
                SampleBuffer sb;
                try {
                    sb = (SampleBuffer) decoder.decodeFrame(h, bits);
                } catch (DecoderException e) {
                    bits.closeFrame();
                    continue;   // trama dañada: se salta
                }
                if (remuestreo == null) remuestreo = new Remuestreador(sb.getSampleFrequency(), sb.getChannelCount());
                short[] salida = remuestreo.convertir(sb.getBuffer(), sb.getBufferLength());
                bits.closeFrame();
                buffer.escribir(salida, salida.length);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | BitstreamException e) {
            error = "No se pudo leer " + archivo.getName() + ": " + e.getMessage();
        } finally {
            terminoDecodificar = true;
        }
    }

    /** Comprueba que el archivo sea un MP3 válido (tiene al menos una trama decodificable). */
    public static boolean esMp3Valido(File f) {
        if (!f.isFile() || !f.getName().toLowerCase().endsWith(".mp3")) return false;
        try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
            Bitstream bits = new Bitstream(in);
            Header h = bits.readFrame();
            if (h == null || h.layer() != 3) return false;
            new Decoder().decodeFrame(h, bits);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static long medirDuracion(File f) {
        try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
            Bitstream bits = new Bitstream(in);
            Header h = bits.readFrame();
            return h == null ? 0 : (long) h.total_ms((int) f.length());
        } catch (Exception e) {
            return 0;
        }
    }

    /** Convierte cualquier frecuencia y número de canales a 44.1 kHz estéreo (interpolación lineal). */
    static class Remuestreador {
        private final int canales;
        private final double paso;
        private double pos = 0;
        private short ultimoI = 0, ultimoD = 0;

        Remuestreador(int frecuencia, int canales) {
            this.canales = canales;
            this.paso = frecuencia / (double) Protocolo.FRECUENCIA;
        }

        short[] convertir(short[] in, int n) {
            int cuadros = n / canales;
            if (paso == 1.0) {
                if (canales == 2) {
                    short[] out = new short[cuadros * 2];
                    System.arraycopy(in, 0, out, 0, out.length);
                    return out;
                }
                short[] out = new short[cuadros * 2];
                for (int i = 0; i < cuadros; i++) out[2 * i] = out[2 * i + 1] = in[i];
                return out;
            }
            // pos recorre los cuadros de entrada; el índice -1 es el último cuadro del bloque anterior
            int max = (int) Math.ceil((cuadros - pos) / paso) + 1;
            short[] out = new short[max * 2];
            int k = 0;
            while (pos < cuadros - 1 && k < max) {
                int i = (int) Math.floor(pos);
                double f = pos - i;
                short iz0 = i < 0 ? ultimoI : izq(in, i), de0 = i < 0 ? ultimoD : der(in, i);
                short iz1 = izq(in, i + 1), de1 = der(in, i + 1);
                out[2 * k] = (short) (iz0 + (iz1 - iz0) * f);
                out[2 * k + 1] = (short) (de0 + (de1 - de0) * f);
                k++;
                pos += paso;
            }
            ultimoI = izq(in, cuadros - 1);
            ultimoD = der(in, cuadros - 1);
            pos -= cuadros;
            short[] recortado = new short[k * 2];
            System.arraycopy(out, 0, recortado, 0, k * 2);
            return recortado;
        }

        private short izq(short[] in, int i) {
            return in[i * canales];
        }

        private short der(short[] in, int i) {
            return in[i * canales + (canales > 1 ? 1 : 0)];
        }
    }
}
