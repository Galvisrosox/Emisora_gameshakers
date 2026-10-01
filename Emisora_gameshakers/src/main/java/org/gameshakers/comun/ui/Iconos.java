package org.gameshakers.comun.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

/** Íconos dibujados con Java2D (no dependen de fuentes ni de imágenes). */
public final class Iconos {

    public enum Tipo { PLAY, PAUSA, STOP, SIGUIENTE, MIC, MIC_OFF, ANTENA, PARLANTE, SILENCIO, MAS, MENOS, BUSCAR }

    public static Icon de(Tipo tipo, int tam, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g2.translate(x, y);
                g2.scale(tam / 24.0, tam / 24.0);
                g2.setColor(c != null && !c.isEnabled() ? color.darker().darker() : color);
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                dibujar(g2, tipo);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return tam;
            }

            @Override
            public int getIconHeight() {
                return tam;
            }
        };
    }

    private static void dibujar(Graphics2D g, Tipo tipo) {
        switch (tipo) {
            case PLAY -> g.fill(triangulo(8, 5, 8, 19, 19, 12));
            case PAUSA -> {
                g.fill(new RoundRectangle2D.Double(6, 5, 4.5, 14, 2, 2));
                g.fill(new RoundRectangle2D.Double(13.5, 5, 4.5, 14, 2, 2));
            }
            case STOP -> g.fill(new RoundRectangle2D.Double(6, 6, 12, 12, 3, 3));
            case SIGUIENTE -> {
                g.fill(triangulo(5, 5, 5, 19, 15, 12));
                g.fill(new RoundRectangle2D.Double(15.5, 5, 3.5, 14, 2, 2));
            }
            case MIC, MIC_OFF -> {
                g.fill(new RoundRectangle2D.Double(8.5, 2.5, 7, 12, 7, 7));
                g.draw(new Arc2D.Double(5, 5, 14, 13, 180, 180, Arc2D.OPEN));
                g.draw(new Line2D.Double(12, 18, 12, 21.5));
                g.draw(new Line2D.Double(8.5, 21.5, 15.5, 21.5));
                if (tipo == Tipo.MIC_OFF) {
                    g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(new Line2D.Double(3.5, 3.5, 20.5, 20.5));
                }
            }
            case ANTENA -> {
                g.fill(new Ellipse2D.Double(10, 9, 4, 4));
                g.draw(new Line2D.Double(12, 13, 8, 22));
                g.draw(new Line2D.Double(12, 13, 16, 22));
                g.draw(new Arc2D.Double(6.5, 5.5, 11, 11, 135, 90, Arc2D.OPEN));
                g.draw(new Arc2D.Double(6.5, 5.5, 11, 11, -45, 90, Arc2D.OPEN));
                g.draw(new Arc2D.Double(2.5, 1.5, 19, 19, 135, 90, Arc2D.OPEN));
                g.draw(new Arc2D.Double(2.5, 1.5, 19, 19, -45, 90, Arc2D.OPEN));
            }
            case PARLANTE, SILENCIO -> {
                Path2D p = new Path2D.Double();
                p.moveTo(3, 9);
                p.lineTo(7, 9);
                p.lineTo(12, 4.5);
                p.lineTo(12, 19.5);
                p.lineTo(7, 15);
                p.lineTo(3, 15);
                p.closePath();
                g.fill(p);
                if (tipo == Tipo.PARLANTE) {
                    g.draw(new Arc2D.Double(9, 8, 8, 8, -50, 100, Arc2D.OPEN));
                    g.draw(new Arc2D.Double(7, 4, 14, 16, -50, 100, Arc2D.OPEN));
                } else {
                    g.draw(new Line2D.Double(15.5, 9, 21, 15));
                    g.draw(new Line2D.Double(21, 9, 15.5, 15));
                }
            }
            case MAS -> {
                g.draw(new Line2D.Double(12, 5, 12, 19));
                g.draw(new Line2D.Double(5, 12, 19, 12));
            }
            case MENOS -> g.draw(new Line2D.Double(5, 12, 19, 12));
            case BUSCAR -> {
                g.draw(new Ellipse2D.Double(4, 4, 11, 11));
                g.draw(new Line2D.Double(13.5, 13.5, 20, 20));
            }
        }
    }

    private static Shape triangulo(double x1, double y1, double x2, double y2, double x3, double y3) {
        Path2D p = new Path2D.Double();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        p.lineTo(x3, y3);
        p.closePath();
        return p;
    }

    private Iconos() {
    }
}
