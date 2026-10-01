package org.gameshakers;

import org.gameshakers.cliente.CliOyente;
import org.gameshakers.comun.Protocolo;
import org.gameshakers.comun.ui.Cabecera;
import org.gameshakers.comun.ui.Iconos;
import org.gameshakers.comun.ui.Tema;
import org.gameshakers.servidor.EmisoraSvr;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Punto de entrada del JAR: pregunta si este equipo será la emisora o un oyente.
 * También se puede abrir directo con "emisora" u "oyente [ip]" como argumentos.
 */
public class Lanzador {

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("emisora")) {
            EmisoraSvr.main(new String[0]);
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("oyente")) {
            CliOyente.main(args.length > 1 ? new String[]{args[1]} : new String[0]);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            Tema.aplicar();
            JFrame f = new JFrame(Protocolo.NOMBRE_EMISORA);
            f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            JPanel raiz = new JPanel(new BorderLayout());
            raiz.setBackground(Tema.FONDO);
            Cabecera cab = new Cabecera(Protocolo.NOMBRE_EMISORA, "Radio por UDP en tu red local");
            raiz.add(cab, BorderLayout.NORTH);

            JPanel opciones = new JPanel(new GridLayout(1, 2, 16, 0));
            opciones.setOpaque(false);
            opciones.setBorder(new EmptyBorder(24, 24, 24, 24));
            JButton emisora = opcion("Soy el locutor", "Transmitir micrófono y MP3", Iconos.Tipo.ANTENA, Tema.ACENTO);
            JButton oyente = opcion("Quiero escuchar", "Conectarme a una emisora", Iconos.Tipo.PARLANTE, Tema.ACENTO_2);
            emisora.addActionListener(e -> { f.dispose(); new EmisoraSvr().setVisible(true); });
            oyente.addActionListener(e -> { f.dispose(); new CliOyente(null).setVisible(true); });
            opciones.add(emisora);
            opciones.add(oyente);
            raiz.add(opciones, BorderLayout.CENTER);

            f.setContentPane(raiz);
            f.setSize(560, 330);
            f.setResizable(false);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }

    private static JButton opcion(String titulo, String detalle, Iconos.Tipo icono, Color color) {
        JButton b = new JButton("<html><center><span style='font-size:14px'><b>" + titulo + "</b></span><br>"
                + "<span style='font-size:10px'>" + detalle + "</span></center></html>",
                Iconos.de(icono, 40, Color.WHITE));
        b.setVerticalTextPosition(SwingConstants.BOTTOM);
        b.setHorizontalTextPosition(SwingConstants.CENTER);
        b.setIconTextGap(12);
        Tema.estiloPrincipal(b, color);
        return b;
    }
}
