package org.gameshakers.comun;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Red {

    /** Direcciones IPv4 de este equipo en la red local (sin 127.0.0.1). */
    public static List<String> ipsLocales() {
        List<String> ips = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (InetAddress dir : Collections.list(ni.getInetAddresses())) {
                    if (dir instanceof Inet4Address) ips.add(dir.getHostAddress());
                }
            }
        } catch (Exception ignorada) {
            // si no se pueden leer las interfaces se muestra la lista vacía
        }
        // primero las privadas típicas de una LAN
        ips.sort((a, b) -> Boolean.compare(!esPrivada(a), !esPrivada(b)));
        return ips;
    }

    private static boolean esPrivada(String ip) {
        return ip.startsWith("192.168.") || ip.startsWith("10.") || ip.matches("172\\.(1[6-9]|2\\d|3[01])\\..*");
    }

    private Red() {
    }
}
