package org.gameshakers.comun.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Panel con esquinas redondeadas y un título, usado para agrupar controles. */
public class Tarjeta extends JPanel {

    private final JPanel contenido = new JPanel();

    public Tarjeta(String titulo) {
        super(new BorderLayout(0, 10));
        setOpaque(false);
        setBorder(new EmptyBorder(14, 16, 16, 16));
        if (titulo != null) {
            JLabel t = Tema.etiqueta(titulo.toUpperCase(), Font.BOLD, 11f, Tema.TEXTO_SUAVE);
            add(t, BorderLayout.NORTH);
        }
        contenido.setOpaque(false);
        add(contenido, BorderLayout.CENTER);
    }

    public JPanel contenido() {
        return contenido;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Tema.TARJETA);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
        g2.setColor(Tema.BORDE);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
        g2.dispose();
    }
}
