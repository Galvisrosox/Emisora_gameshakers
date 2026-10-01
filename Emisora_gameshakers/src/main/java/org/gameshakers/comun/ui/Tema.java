package org.gameshakers.comun.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/** Colores y apariencia comunes a las dos ventanas. */
public final class Tema {

    public static final Color FONDO = new Color(0x14, 0x12, 0x1F);
    public static final Color TARJETA = new Color(0x1F, 0x1C, 0x2E);
    public static final Color BORDE = new Color(0x2E, 0x2A, 0x44);
    public static final Color TEXTO = new Color(0xEC, 0xEA, 0xF5);
    public static final Color TEXTO_SUAVE = new Color(0x9A, 0x96, 0xB5);
    public static final Color ACENTO = new Color(0xFF, 0x3D, 0x7F);     // rosa
    public static final Color ACENTO_2 = new Color(0x7C, 0x4D, 0xFF);   // violeta
    public static final Color VERDE = new Color(0x2E, 0xE5, 0x9D);
    public static final Color AMARILLO = new Color(0xFF, 0xC8, 0x3D);
    public static final Color ROJO = new Color(0xFF, 0x4D, 0x4D);

    public static void aplicar() {
        FlatLaf.setGlobalExtraDefaults(Map.of(
                "@accentColor", hex(ACENTO),
                "@background", hex(FONDO),
                "@foreground", hex(TEXTO)));
        FlatDarkLaf.setup();
        UIManager.put("Component.arc", 14);
        UIManager.put("Button.arc", 14);
        UIManager.put("TextComponent.arc", 12);
        UIManager.put("ProgressBar.arc", 999);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("List.selectionBackground", ACENTO_2.darker());
        UIManager.put("Slider.thumbColor", ACENTO);
        UIManager.put("Slider.trackValueColor", ACENTO);
        UIManager.put("ProgressBar.foreground", ACENTO);
        UIManager.put("defaultFont", new Font(Font.SANS_SERIF, Font.PLAIN, 13));
    }

    public static Font fuente(int estilo, float tam) {
        return UIManager.getFont("defaultFont").deriveFont(estilo, tam);
    }

    /** Botón destacado (relleno de color). */
    public static JButton botonPrincipal(String texto, Color color) {
        JButton b = new JButton(texto);
        estiloPrincipal(b, color);
        return b;
    }

    public static void estiloPrincipal(AbstractButton b, Color color) {
        b.putClientProperty("FlatLaf.style",
                "background:" + hex(color) + ";foreground:#FFFFFF;borderWidth:0;focusWidth:0;"
                        + "hoverBackground:" + hex(color.brighter()) + ";pressedBackground:" + hex(color.darker()) + ";font:bold +1");
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /** Botón redondo para los controles de reproducción. */
    public static JButton botonRedondo(Icon icono, String ayuda) {
        JButton b = new JButton(icono);
        b.setToolTipText(ayuda);
        b.putClientProperty("JButton.buttonType", "roundRect");
        b.putClientProperty("FlatLaf.style", "arc:999;background:#2E2A44;borderWidth:0;focusWidth:0;hoverBackground:#3D3860");
        b.setPreferredSize(new Dimension(44, 44));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    public static JLabel etiqueta(String texto, int estilo, float tam, Color color) {
        JLabel l = new JLabel(texto);
        l.setFont(fuente(estilo, tam));
        l.setForeground(color);
        return l;
    }

    public static String hex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private Tema() {
    }
}
