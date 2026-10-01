package org.gameshakers.comun;

import java.net.DatagramPacket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;

/** Ayudas para construir y leer los datagramas del protocolo. */
public final class Datagrama {

    /** Mensaje de texto. El tamaño se cuenta en bytes UTF-8, así no se cortan las tildes. */
    public static DatagramPacket texto(SocketAddress destino, String mensaje) {
        byte[] datos = mensaje.getBytes(StandardCharsets.UTF_8);
        return new DatagramPacket(datos, datos.length, destino);
    }

    public static String leerTexto(DatagramPacket paquete) {
        return new String(paquete.getData(), paquete.getOffset(), paquete.getLength(), StandardCharsets.UTF_8).trim();
    }

    public static boolean esAudio(DatagramPacket paquete) {
        return paquete.getLength() > Protocolo.CABECERA_AUDIO
                && paquete.getData()[paquete.getOffset()] == Protocolo.TIPO_AUDIO;
    }

    /** Arma el contenido de un paquete de audio: [0][secuencia][mp3]. */
    public static byte[] audio(int secuencia, byte[] mp3, int largo) {
        byte[] datos = new byte[Protocolo.CABECERA_AUDIO + largo];
        datos[0] = Protocolo.TIPO_AUDIO;
        datos[1] = (byte) (secuencia >>> 24);
        datos[2] = (byte) (secuencia >>> 16);
        datos[3] = (byte) (secuencia >>> 8);
        datos[4] = (byte) secuencia;
        System.arraycopy(mp3, 0, datos, Protocolo.CABECERA_AUDIO, largo);
        return datos;
    }

    public static int leerSecuencia(DatagramPacket paquete) {
        byte[] d = paquete.getData();
        int o = paquete.getOffset();
        return ((d[o + 1] & 0xFF) << 24) | ((d[o + 2] & 0xFF) << 16) | ((d[o + 3] & 0xFF) << 8) | (d[o + 4] & 0xFF);
    }

    private Datagrama() {
    }
}
