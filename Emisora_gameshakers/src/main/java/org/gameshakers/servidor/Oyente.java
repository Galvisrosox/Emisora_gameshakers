package org.gameshakers.servidor;

import java.net.InetSocketAddress;

/** Un equipo que está escuchando la emisora. */
public class Oyente {

    final InetSocketAddress direccion;
    volatile String nombre;
    volatile long ultimaSenal;

    Oyente(InetSocketAddress direccion, String nombre) {
        this.direccion = direccion;
        this.nombre = nombre;
        this.ultimaSenal = System.currentTimeMillis();
    }

    public String nombre() {
        return nombre;
    }

    public String ip() {
        return direccion.getAddress().getHostAddress();
    }

    @Override
    public String toString() {
        return nombre + " (" + ip() + ")";
    }
}
