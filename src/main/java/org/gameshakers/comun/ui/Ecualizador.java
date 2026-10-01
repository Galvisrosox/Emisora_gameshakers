package org.gameshakers.comun.ui;

import javax.swing.*;
import java.awt.*;

/** Barras animadas que muestran la energía de varias bandas de frecuencia. */
public class Ecualizador extends JComponent {

    private final float[] barras;
    private final float[] picos;

    public Ecualizador(int bandas) {
        barras = new float[bandas];
        picos = new float[bandas];
        setPreferredSize(new Dimension(300, 120));
    }

    /** Valores 0..1 por banda (null o vacío = silencio). */
    public void setBandas(float[] valores) {
        for (int i = 0; i < barras.length; i++) {
            float v = valores != null && i < valores.length ? valores[i] : 0f;
            barras[i] = v > barras[i] ? v : barras[i] * 0.85f;
            picos[i] = Math.max(picos[i] - 0.012f, barras[i]);
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        int n = barras.length;
        float hueco = 6f;
        float ancho = (w - hueco * (n - 1)) / n;
        GradientPaint degradado = new GradientPaint(0, h, Tema.ACENTO_2, 0, 0, Tema.ACENTO);
        for (int i = 0; i < n; i++) {
            float x = i * (ancho + hueco);
            float alto = Math.max(4f, barras[i] * (h - 6));
            g2.setColor(new Color(255, 255, 255, 14));
            g2.fillRoundRect((int) x, 0, (int) ancho, h, 8, 8);
            g2.setPaint(degradado);
            g2.fillRoundRect((int) x, (int) (h - alto), (int) ancho, (int) alto, 8, 8);
            int yp = (int) (h - Math.max(4f, picos[i] * (h - 6))) - 4;
            g2.setColor(new Color(255, 255, 255, 170));
            g2.fillRoundRect((int) x, Math.max(0, yp), (int) ancho, 3, 3, 3);
        }
        g2.dispose();
    }
}
