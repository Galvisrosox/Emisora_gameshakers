package org.gameshakers.servidor;

import org.gameshakers.comun.Datagrama;
import org.gameshakers.comun.Protocolo;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Socket UDP de la emisora: atiende a los oyentes y les reparte el audio.
 * Cada paquete de audio se envía a cada oyente registrado (unicast).
 */
public class ServidorUdp {

    private final Map<InetSocketAddress, Oyente> oyentes = new ConcurrentHashMap<>();
    private final Consumer<String> registro;
    private final Runnable alCambiarOyentes;
    private final Supplier<String> info;

    private DatagramSocket socket;
    private ScheduledExecutorService tareas;
    private volatile boolean activo;
    private int secuencia = 0;

    /**
     * @param registro         recibe mensajes para mostrar en la bitácora
     * @param alCambiarOyentes se llama cuando alguien entra o sale
     * @param info             texto "titulo|mic|..." que se envía en los mensajes INFO
     */
    public ServidorUdp(Consumer<String> registro, Runnable alCambiarOyentes, Supplier<String> info) {
        this.registro = registro;
        this.alCambiarOyentes = alCambiarOyentes;
        this.info = info;
    }

    public void iniciar(int puerto) throws SocketException {
        socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(puerto));
        socket.setSendBufferSize(1 << 20);
        activo = true;

        Thread receptor = new Thread(this::recibir, "emisora-receptor");
        receptor.setDaemon(true);
        receptor.start();

        tareas = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "emisora-tareas");
            t.setDaemon(true);
            return t;
        });
        tareas.scheduleAtFixedRate(this::revisarOyentes, 1, 1, TimeUnit.SECONDS);
        tareas.scheduleAtFixedRate(this::enviarInfo, 2, 2, TimeUnit.SECONDS);
    }

    public void detener() {
        if (!activo) return;
        activo = false;
        for (Oyente o : oyentes.values()) enviarTexto(o.direccion, Protocolo.ADIOS);
        oyentes.clear();
        alCambiarOyentes.run();
        if (tareas != null) tareas.shutdownNow();
        if (socket != null) socket.close();
    }

    public boolean activo() {
        return activo;
    }

    public List<Oyente> oyentes() {
        List<Oyente> lista = new ArrayList<>(oyentes.values());
        lista.sort((a, b) -> a.nombre.compareToIgnoreCase(b.nombre));
        return lista;
    }

    /** Envía el mismo bloque MP3 a todos los oyentes. */
    public void enviarAudio(byte[] mp3) {
        if (!activo || oyentes.isEmpty()) return;
        // se copia en un arreglo propio: el que llega se reutiliza para la siguiente trama
        byte[] datos = Datagrama.audio(secuencia++, mp3, mp3.length);
        for (Oyente o : oyentes.values()) {
            try {
                socket.send(new DatagramPacket(datos, datos.length, o.direccion));
            } catch (IOException e) {
                // un oyente con problemas no detiene el envío a los demás
            }
        }
    }

    /** Envía de inmediato la información de "sonando ahora" a todos. */
    public void enviarInfo() {
        if (!activo) return;
        String msg = Protocolo.INFO + Protocolo.SEP + info.get() + Protocolo.SEP + oyentes.size();
        for (Oyente o : oyentes.values()) enviarTexto(o.direccion, msg);
    }

    private void recibir() {
        byte[] buf = new byte[1024];
        while (activo) {
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(p);
            } catch (IOException e) {
                if (activo) registro.accept("Error al recibir: " + e.getMessage());
                continue;
            }
            InetSocketAddress origen = (InetSocketAddress) p.getSocketAddress();
            String[] partes = Datagrama.leerTexto(p).split(Protocolo.SEP_REGEX, 2);
            String comando = partes[0];
            switch (comando) {
                case Protocolo.HOLA -> {
                    String nombre = partes.length > 1 && !partes[1].isBlank() ? limpiar(partes[1]) : "Oyente";
                    Oyente previo = oyentes.put(origen, new Oyente(origen, nombre));
                    enviarTexto(origen, Protocolo.BIENVENIDO + Protocolo.SEP + Protocolo.NOMBRE_EMISORA);
                    if (previo == null) {
                        registro.accept("> Se conectó " + nombre + " desde " + origen.getAddress().getHostAddress());
                        cambiaronOyentes();
                    }
                    enviarInfo();
                }
                case Protocolo.PING -> {
                    Oyente o = oyentes.get(origen);
                    if (o != null) o.ultimaSenal = System.currentTimeMillis();
                    else enviarTexto(origen, Protocolo.QUIEN);
                }
                case Protocolo.CHAO -> {
                    Oyente o = oyentes.remove(origen);
                    if (o != null) {
                        registro.accept("< Se desconectó " + o.nombre);
                        cambiaronOyentes();
                    }
                }
                case Protocolo.BUSCAR -> enviarTexto(origen, Protocolo.AQUI + Protocolo.SEP + Protocolo.NOMBRE_EMISORA);
                default -> { /* mensaje desconocido: se ignora */ }
            }
        }
    }

    /** Saca de la lista a quien lleva demasiado tiempo sin enviar PING. */
    private void revisarOyentes() {
        long ahora = System.currentTimeMillis();
        boolean cambio = false;
        for (Oyente o : oyentes.values()) {
            if (ahora - o.ultimaSenal > Protocolo.TIEMPO_MAX_SIN_PING_MS && oyentes.remove(o.direccion, o)) {
                registro.accept("< Se desconectó " + o.nombre + " (sin respuesta)");
                cambio = true;
            }
        }
        if (cambio) cambiaronOyentes();
    }

    private void cambiaronOyentes() {
        List<Oyente> lista = oyentes();
        StringBuilder sb = new StringBuilder("   Oyentes conectados (" + lista.size() + "): ");
        if (lista.isEmpty()) sb.append("nadie");
        for (int i = 0; i < lista.size(); i++) sb.append(i > 0 ? ", " : "").append(lista.get(i).nombre);
        registro.accept(sb.toString());
        alCambiarOyentes.run();
    }

    private void enviarTexto(SocketAddress destino, String msg) {
        try {
            socket.send(Datagrama.texto(destino, msg));
        } catch (IOException ignorada) {
            // UDP no garantiza entrega; el siguiente mensaje lo intentará otra vez
        }
    }

    private static String limpiar(String nombre) {
        String n = nombre.replace(Protocolo.SEP, " ").strip();
        return n.length() > 30 ? n.substring(0, 30) : n;
    }
}
