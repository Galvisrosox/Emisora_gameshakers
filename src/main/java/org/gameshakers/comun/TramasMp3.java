package org.gameshakers.comun;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Separa un flujo de bytes MP3 en tramas completas.
 *
 * El codificador entrega trozos que no coinciden con los límites de trama; aquí se
 * acumulan y se devuelven tramas enteras, para que cada paquete UDP lleve tramas
 * completas y la pérdida de un paquete no deje tramas partidas.
 */
public class TramasMp3 {

    private static final int[][] BITRATES = {
            // MPEG-1 Layer III
            {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, -1},
            // MPEG-2 / 2.5 Layer III
            {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, -1}
    };
    private static final int[][] FRECUENCIAS = {
            {44100, 48000, 32000}, // MPEG-1
            {22050, 24000, 16000}, // MPEG-2
            {11025, 12000, 8000}   // MPEG-2.5
    };

    private byte[] buf = new byte[16384];
    private int largo = 0;

    public List<byte[]> agregar(byte[] datos, int n) {
        if (largo + n > buf.length) {
            byte[] nuevo = new byte[Math.max(buf.length * 2, largo + n)];
            System.arraycopy(buf, 0, nuevo, 0, largo);
            buf = nuevo;
        }
        System.arraycopy(datos, 0, buf, largo, n);
        largo += n;

        List<byte[]> tramas = new ArrayList<>();
        int pos = 0;
        while (pos + 4 <= largo) {
            int tam = tamTrama(buf, pos);
            if (tam <= 0) {
                pos++;               // no es cabecera: buscar la siguiente sincronía
                continue;
            }
            if (pos + tam > largo) {
                break;               // trama incompleta: esperar más bytes
            }
            byte[] t = new byte[tam];
            System.arraycopy(buf, pos, t, 0, tam);
            tramas.add(t);
            pos += tam;
        }
        System.arraycopy(buf, pos, buf, 0, largo - pos);
        largo -= pos;
        return tramas;
    }

    /** Devuelve el tamaño en bytes de la trama Layer III que empieza en {@code p}, o -1. */
    public static int tamTrama(byte[] b, int p) {
        int h = ((b[p] & 0xFF) << 24) | ((b[p + 1] & 0xFF) << 16) | ((b[p + 2] & 0xFF) << 8) | (b[p + 3] & 0xFF);
        if ((h & 0xFFE00000) != 0xFFE00000) return -1;
        int version = (h >>> 19) & 3;      // 3 = MPEG-1, 2 = MPEG-2, 0 = MPEG-2.5
        int capa = (h >>> 17) & 3;         // 1 = Layer III
        int iBitrate = (h >>> 12) & 0xF;
        int iFrec = (h >>> 10) & 3;
        int relleno = (h >>> 9) & 1;
        if (version == 1 || capa != 1 || iBitrate == 0 || iBitrate == 15 || iFrec == 3) return -1;
        boolean mpeg1 = version == 3;
        int kbps = BITRATES[mpeg1 ? 0 : 1][iBitrate];
        int frec = FRECUENCIAS[mpeg1 ? 0 : (version == 2 ? 1 : 2)][iFrec];
        return (mpeg1 ? 144_000 : 72_000) * kbps / frec + relleno;
    }

    /** Une varias tramas en un solo arreglo. */
    public static byte[] unir(List<byte[]> tramas) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] t : tramas) out.writeBytes(t);
        return out.toByteArray();
    }
}
