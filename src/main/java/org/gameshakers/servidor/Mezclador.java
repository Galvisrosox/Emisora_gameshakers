package org.gameshakers.servidor;

import de.sciss.jump3r.lowlevel.LameEncoder;
import org.gameshakers.comun.Protocolo;
import org.gameshakers.comun.TramasMp3;
import org.gameshakers.comun.ui.Analizador;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Corazón de la emisora. A ritmo de tiempo real, cada 26 ms:
 *  1. toma 1152 muestras de la canción y del micrófono,
 *  2. las mezcla con sus volúmenes,
 *  3. las codifica a MP3 (128 kbps, 44.1 kHz, estéreo),
 *  4. agrupa tramas completas y las entrega para enviarlas por UDP.
 * Así los oyentes siempre reciben un único flujo MP3 continuo.
 */
public class Mezclador {

    private static final int N = Protocolo.MUESTRAS_POR_TRAMA;
    private static final long NANOS_POR_BLOQUE = (long) (N * 1_000_000_000L / Protocolo.FRECUENCIA);

    private final Consumer<byte[]> salida;
    private final FuenteMicrofono microfono;
    private volatile FuenteMp3 cancion;
    private volatile Runnable alTerminarCancion = () -> { };

    private volatile boolean activo;
    private volatile boolean pausado;
    private volatile float volMusica = 0.8f;
    private volatile float volMicrofono = 1.0f;
    private volatile boolean atenuarAlHablar = true;
    private volatile boolean monitor = false;
    private float gananciaMusica = 1f;    // suaviza la atenuación

    private volatile float nivelMic, nivelSalida;
    private volatile float[] bandas = new float[Analizador.FRECUENCIAS.length];

    private Thread hilo;

    public Mezclador(FuenteMicrofono microfono, Consumer<byte[]> salida) {
        this.microfono = microfono;
        this.salida = salida;
    }

    public void iniciar() {
        activo = true;
        hilo = new Thread(this::ciclo, "mezclador");
        hilo.setPriority(Thread.MAX_PRIORITY);
        hilo.setDaemon(true);
        hilo.start();
    }

    public void detener() {
        activo = false;
        if (hilo != null) hilo.interrupt();
    }

    /** Cambia la canción que suena (null = ninguna). */
    public void setCancion(FuenteMp3 nueva, Runnable alTerminar) {
        FuenteMp3 vieja = cancion;
        cancion = nueva;
        alTerminarCancion = alTerminar != null ? alTerminar : () -> { };
        if (vieja != null && vieja != nueva) vieja.cancelar();
    }

    public FuenteMp3 cancion() {
        return cancion;
    }

    public void setPausado(boolean p) {
        pausado = p;
    }

    public boolean pausado() {
        return pausado;
    }

    public void setVolMusica(float v) {
        volMusica = v;
    }

    public void setVolMicrofono(float v) {
        volMicrofono = v;
    }

    public void setAtenuarAlHablar(boolean a) {
        atenuarAlHablar = a;
    }

    public void setMonitor(boolean m) {
        monitor = m;
    }

    public float nivelMic() {
        return nivelMic;
    }

    public float nivelSalida() {
        return nivelSalida;
    }

    public float[] bandas() {
        return bandas;
    }

    private void ciclo() {
        AudioFormat estereo = Protocolo.formatoPcm(2);
        LameEncoder codificador = new LameEncoder(estereo, Protocolo.KBPS,
                LameEncoder.CHANNEL_MODE_JOINT_STEREO, LameEncoder.QUALITY_LOW, false);
        byte[] mp3 = new byte[codificador.getMP3BufferSize()];
        TramasMp3 separador = new TramasMp3();
        List<byte[]> pendientes = new ArrayList<>();

        short[] musica = new short[N * 2];
        short[] voz = new short[N];
        short[] mezcla = new short[N * 2];
        byte[] pcm = new byte[N * 4];
        byte[] pcmMonitor = new byte[N * 4];
        SourceDataLine lineaMonitor = null;

        long siguiente = System.nanoTime();
        try {
            while (activo) {
                // 1. canción
                int nm = 0;
                FuenteMp3 c = cancion;
                if (c != null && !pausado) {
                    nm = c.leer(musica, musica.length);
                    if (c.termino()) {
                        cancion = null;
                        alTerminarCancion.run();
                    }
                }
                for (int i = nm; i < musica.length; i++) musica[i] = 0;

                // 2. micrófono
                int nv = microfono.activo() ? microfono.leer(voz, N) : 0;
                for (int i = nv; i < N; i++) voz[i] = 0;
                nivelMic = microfono.activo() ? Analizador.rms(voz, N) : 0f;

                // 3. mezcla (con atenuación suave de la música cuando el locutor habla)
                float objetivo = atenuarAlHablar && microfono.activo() && nivelMic > 0.02f ? 0.3f : 1f;
                float vm = volMusica, vv = volMicrofono;
                for (int i = 0; i < N; i++) {
                    gananciaMusica += (objetivo - gananciaMusica) * 0.0008f;
                    float g = vm * gananciaMusica;
                    float v = voz[i] * vv;
                    mezcla[2 * i] = recortar(musica[2 * i] * g + v);
                    mezcla[2 * i + 1] = recortar(musica[2 * i + 1] * g + v);
                    if (monitor) {   // el monitor reproduce solo la música, para no acoplar el micrófono
                        ponerMuestra(pcmMonitor, 2 * i, recortar(musica[2 * i] * g));
                        ponerMuestra(pcmMonitor, 2 * i + 1, recortar(musica[2 * i + 1] * g));
                    }
                }
                for (int i = 0; i < mezcla.length; i++) ponerMuestra(pcm, i, mezcla[i]);
                nivelSalida = Analizador.rms(mezcla, mezcla.length);
                bandas = Analizador.bandas(mezcla, mezcla.length, 2, Protocolo.FRECUENCIA);

                // 4. codificar a MP3 y enviar tramas completas
                int k = codificador.encodeBuffer(pcm, 0, pcm.length, mp3);
                if (k > 0) {
                    pendientes.addAll(separador.agregar(mp3, k));
                    while (pendientes.size() >= Protocolo.TRAMAS_POR_PAQUETE) {
                        List<byte[]> grupo = pendientes.subList(0, Protocolo.TRAMAS_POR_PAQUETE);
                        salida.accept(TramasMp3.unir(grupo));
                        grupo.clear();
                    }
                }

                // monitor local (sin bloquear el reloj de la transmisión)
                if (monitor) {
                    if (lineaMonitor == null) {
                        lineaMonitor = abrirMonitor(estereo);
                        if (lineaMonitor == null) monitor = false;   // este equipo no tiene salida de audio
                    }
                    if (lineaMonitor != null && lineaMonitor.available() >= pcmMonitor.length) {
                        lineaMonitor.write(pcmMonitor, 0, pcmMonitor.length);
                    }
                } else if (lineaMonitor != null) {
                    lineaMonitor.close();
                    lineaMonitor = null;
                }

                // 5. esperar al siguiente bloque para ir exactamente a tiempo real
                siguiente += NANOS_POR_BLOQUE;
                long espera = siguiente - System.nanoTime();
                if (espera > 0) {
                    Thread.sleep(espera / 1_000_000, (int) (espera % 1_000_000));
                } else if (espera < -200_000_000L) {
                    siguiente = System.nanoTime();   // se atrasó mucho (equipo ocupado): no intentar recuperar
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            codificador.close();
            if (lineaMonitor != null) lineaMonitor.close();
            nivelMic = nivelSalida = 0f;
            bandas = new float[Analizador.FRECUENCIAS.length];
        }
    }

    private static SourceDataLine abrirMonitor(AudioFormat formato) {
        try {
            SourceDataLine l = AudioSystem.getSourceDataLine(formato);
            l.open(formato, N * 4 * 12);
            l.start();
            return l;
        } catch (LineUnavailableException | IllegalArgumentException e) {
            return null;
        }
    }

    private static short recortar(float v) {
        return (short) Math.max(-32768, Math.min(32767, v));
    }

    private static void ponerMuestra(byte[] b, int i, short s) {
        b[2 * i] = (byte) s;
        b[2 * i + 1] = (byte) (s >> 8);
    }
}
