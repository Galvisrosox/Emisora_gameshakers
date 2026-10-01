package org.gameshakers.comun.ui;

import javax.swing.*;
import java.awt.*;

/** Medidor horizontal de nivel (0..1) con marca de pico. */
public class VuMetro extends JComponent {

    private float nivel = 0f;
    private float pico = 0f;
    private long picoHasta = 0;

    public VuMetro() {
        setPreferredSize(new Dimension(160, 12));
        setMinimumSize(new Dimension(40, 10));
    }

    /** Recibe un nivel RMS lineal (0..1); se muestra en escala de dB. */
    public void setNivel(float rms) {
        float db = rms <= 0.00001f ? -60f : (float) (20 * Math.log10(rms));
        float v = Math.max(0f, Math.min(1f, (db + 50f) / 50f));
        nivel = v > nivel ? v : nivel * 0.82f + v * 0.18f;
        long ahora = System.currentTimeMillis();
        if (v >= pico || ahora > picoHasta) {
            pico = Math.max(v, pico * 0.95f);
            if (v >= pico) picoHasta = ahora + 900;
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g2.setColor(Tema.BORDE);
        g2.fillRoundRect(0, 0, w, h, h, h);
        int ancho = (int) (w * nivel);
        if (ancho > 0) {
            g2.setPaint(new LinearGradientPaint(0, 0, w, 0, new float[]{0f, 0.65f, 0.85f, 1f},
                    new Color[]{Tema.VERDE, Tema.VERDE, Tema.AMARILLO, Tema.ROJO}));
            g2.setClip(0, 0, ancho, h);
            g2.fillRoundRect(0, 0, w, h, h, h);
            g2.setClip(null);
        }
        int xp = (int) (w * pico);
        if (xp > 2) {
            g2.setColor(Color.WHITE);
            g2.fillRect(Math.min(xp, w - 3), 1, 2, h - 2);
        }
        g2.dispose();
    }
}
