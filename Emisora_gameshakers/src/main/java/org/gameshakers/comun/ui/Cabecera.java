package org.gameshakers.comun.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Franja superior con degradado, el nombre de la emisora y el indicador EN VIVO. */
public class Cabecera extends JPanel {

    private final JLabel titulo;
    private final JLabel subtitulo;
    private final Indicador indicador = new Indicador();

    public Cabecera(String textoTitulo, String textoSubtitulo) {
        super(new BorderLayout(14, 0));
        setBorder(new EmptyBorder(18, 22, 18, 22));
        setOpaque(false);

        JLabel logo = new JLabel(Iconos.de(Iconos.Tipo.ANTENA, 40, Color.WHITE));
        add(logo, BorderLayout.WEST);

        JPanel textos = new JPanel(new GridLayout(2, 1));
        textos.setOpaque(false);
        titulo = Tema.etiqueta(textoTitulo, Font.BOLD, 24f, Color.WHITE);
        subtitulo = Tema.etiqueta(textoSubtitulo, Font.PLAIN, 13f, new Color(255, 255, 255, 200));
        textos.add(titulo);
        textos.add(subtitulo);
        add(textos, BorderLayout.CENTER);

        JPanel der = new JPanel(new GridBagLayout());
        der.setOpaque(false);
        der.add(indicador);
        add(der, BorderLayout.EAST);

        new Timer(50, e -> indicador.repaint()).start();
    }

    public void setTitulo(String t) {
        titulo.setText(t);
    }

    public void setSubtitulo(String t) {
        subtitulo.setText(t);
    }

    public void setEnVivo(boolean enVivo, String texto) {
        indicador.enVivo = enVivo;
        indicador.texto = texto;
        indicador.revalidate();
        indicador.repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setPaint(new GradientPaint(0, 0, Tema.ACENTO_2, getWidth(), getHeight(), Tema.ACENTO));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
    }

    /** Píldora "EN VIVO" con un punto que late. */
    private static class Indicador extends JComponent {
        boolean enVivo = false;
        String texto = "FUERA DEL AIRE";

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(Tema.fuente(Font.BOLD, 12f));
            return new Dimension(fm.stringWidth(texto) + 44, 30);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(enVivo ? new Color(0, 0, 0, 90) : new Color(0, 0, 0, 60));
            g2.fillRoundRect(0, 0, w, h, h, h);
            double fase = (System.currentTimeMillis() % 1200) / 1200.0;
            int alfa = enVivo ? (int) (140 + 115 * Math.sin(fase * 2 * Math.PI)) : 110;
            Color punto = enVivo ? new Color(255, 70, 70, alfa) : new Color(200, 200, 200, alfa);
            g2.setColor(punto);
            g2.fillOval(12, h / 2 - 5, 10, 10);
            g2.setFont(Tema.fuente(Font.BOLD, 12f));
            g2.setColor(Color.WHITE);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(texto, 30, (h + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }
}
