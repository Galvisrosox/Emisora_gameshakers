package org.gameshakers.comun;

import javax.sound.sampled.AudioFormat;

/**
 * Reglas que comparten la emisora y los oyentes.
 *
 * Todo viaja por UDP al puerto {@link #PUERTO}.
 *
 * Oyente -> Emisora (texto UTF-8):
 *   HOLA|nombre   se une a la emisora
 *   PING          "sigo aquí" (cada 3 s)
 *   CHAO          se va
 *   BUSCAR        enviado por broadcast para encontrar emisoras en la red
 *
 * Emisora -> Oyente:
 *   BIENVENIDO|nombreEmisora
 *   INFO|titulo|microfono(1/0)|oyentes
 *   QUIEN         la emisora no conoce a quien envía PING (p. ej. se reinició): hay que repetir HOLA
 *   AQUI|nombreEmisora   respuesta a BUSCAR
 *   ADIOS         la emisora se apagó
 *   [0x00][secuencia: 4 bytes][tramas MP3 completas]   audio
 */
public final class Protocolo {

    public static final int PUERTO = 5000;
    public static final String NOMBRE_EMISORA = "GameShakers FM";

    /** Primer byte de un paquete de audio. Los mensajes de texto nunca empiezan por 0. */
    public static final byte TIPO_AUDIO = 0;
    public static final int CABECERA_AUDIO = 5;

    public static final String HOLA = "HOLA";
    public static final String PING = "PING";
    public static final String CHAO = "CHAO";
    public static final String BUSCAR = "BUSCAR";
    public static final String BIENVENIDO = "BIENVENIDO";
    public static final String INFO = "INFO";
    public static final String QUIEN = "QUIEN";
    public static final String AQUI = "AQUI";
    public static final String ADIOS = "ADIOS";

    public static final long INTERVALO_PING_MS = 3_000;
    /** La emisora saca de la lista a quien lleva este tiempo sin dar señales. */
    public static final long TIEMPO_MAX_SIN_PING_MS = 10_000;
    /** El oyente da la conexión por perdida si no recibe nada en este tiempo. */
    public static final long TIEMPO_MAX_SIN_EMISORA_MS = 15_000;

    /** Formato del audio que se mezcla y se codifica a MP3. */
    public static final float FRECUENCIA = 44_100f;
    public static final int CANALES = 2;
    public static final int MUESTRAS_POR_TRAMA = 1152;   // una trama MP3 (MPEG-1 Layer III)
    public static final int KBPS = 128;
    /** Tramas MP3 por paquete UDP (~836 bytes a 128 kbps, cabe sin fragmentar). */
    public static final int TRAMAS_POR_PAQUETE = 2;

    public static AudioFormat formatoPcm(int canales) {
        return new AudioFormat(FRECUENCIA, 16, canales, true, false);
    }

    /** Tamaño máximo de paquete que se espera recibir. */
    public static final int TAM_MAX_PAQUETE = 8192;

    public static final String SEP = "|";
    public static final String SEP_REGEX = "\\|";

    private Protocolo() {
    }
}
