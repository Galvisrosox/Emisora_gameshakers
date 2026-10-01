package org.gameshakers.servidor;

import org.gameshakers.comun.Protocolo;
import org.gameshakers.comun.Red;
import org.gameshakers.comun.ui.*;

import javax.sound.sampled.LineUnavailableException;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.net.SocketException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Ventana del locutor: enciende la emisora, comparte el micrófono y reproduce
 * una lista de canciones MP3 que se transmite por UDP a todos los oyentes.
 */
public class EmisoraSvr extends JFrame {

    private final FuenteMicrofono microfono = new FuenteMicrofono();
    private final ServidorUdp servidor = new ServidorUdp(this::registrar, this::refrescarOyentes, this::infoActual);
    private volatile Mezclador mezclador;

    private final DefaultListModel<File> lista = new DefaultListModel<>();
    private int indiceActual = -1;

    private final Cabecera cabecera = new Cabecera(Protocolo.NOMBRE_EMISORA, "Emisora apagada");
    private final JButton btEmisora = Tema.botonPrincipal("Iniciar emisora", Tema.ACENTO);
    private final JLabel lbConexion = Tema.etiqueta("", Font.PLAIN, 12f, Tema.TEXTO_SUAVE);

    private final JToggleButton btMic = new JToggleButton("Abrir micrófono", Iconos.de(Iconos.Tipo.MIC_OFF, 22, Color.WHITE));
    private final VuMetro vuMic = new VuMetro();
    private final JSlider slMic = new JSlider(0, 150, 100);
    private final JCheckBox chAtenuar = new JCheckBox("Bajar la música mientras hablo", true);

    private final JLabel lbSonando = Tema.etiqueta("Nada sonando", Font.BOLD, 20f, Tema.TEXTO);
    private final JLabel lbEstado = Tema.etiqueta("Agrega canciones MP3 a la lista", Font.PLAIN, 12f, Tema.TEXTO_SUAVE);
    private final JProgressBar progreso = new JProgressBar(0, 1000);
    private final JLabel lbTiempo = Tema.etiqueta("0:00 / 0:00", Font.PLAIN, 12f, Tema.TEXTO_SUAVE);
    private final JButton btPlay = Tema.botonRedondo(Iconos.de(Iconos.Tipo.PLAY, 20, Color.WHITE), "Reproducir / Pausar");
    private final JButton btStop = Tema.botonRedondo(Iconos.de(Iconos.Tipo.STOP, 18, Color.WHITE), "Detener");
    private final JButton btSiguiente = Tema.botonRedondo(Iconos.de(Iconos.Tipo.SIGUIENTE, 18, Color.WHITE), "Siguiente");
    private final JSlider slMusica = new JSlider(0, 100, 80);
    private final Ecualizador ecualizador = new Ecualizador(Analizador.FRECUENCIAS.length);
    private final VuMetro vuSalida = new VuMetro();
    private final JCheckBox chMonitor = new JCheckBox("Escuchar la música en este equipo");

    private final JList<File> jlLista = new JList<>(lista);
    private final DefaultListModel<Oyente> modeloOyentes = new DefaultListModel<>();
    private final JLabel lbOyentes = Tema.etiqueta("0", Font.BOLD, 28f, Tema.TEXTO);
    private final JTextArea bitacora = new JTextArea();

    public EmisoraSvr() {
        super(Protocolo.NOMBRE_EMISORA + " · Emisora");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                apagar();
                dispose();
                System.exit(0);
            }
        });
        construir();
        actualizarControles();
        new Timer(40, e -> animar()).start();
        setMinimumSize(new Dimension(1040, 680));
        setSize(1180, 760);
        setLocationRelativeTo(null);
    }

    // ------------------------------------------------------------------ interfaz

    private void construir() {
        JPanel raiz = new JPanel(new BorderLayout());
        raiz.setBackground(Tema.FONDO);
        raiz.add(cabecera, BorderLayout.NORTH);

        JPanel cuerpo = new JPanel(new GridBagLayout());
        cuerpo.setOpaque(false);
        cuerpo.setBorder(new EmptyBorder(16, 16, 16, 16));
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(0, 0, 0, 16);
        c.weighty = 1;

        c.gridx = 0;
        c.weightx = 0;
        cuerpo.add(columnaIzquierda(), c);
        c.gridx = 1;
        c.weightx = 1;
        cuerpo.add(columnaCentral(), c);
        c.gridx = 2;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 0, 0);
        cuerpo.add(columnaDerecha(), c);

        raiz.add(cuerpo, BorderLayout.CENTER);
        setContentPane(raiz);
    }

    private JComponent columnaIzquierda() {
        JPanel col = new JPanel(new BorderLayout(0, 16));
        col.setOpaque(false);
        col.setPreferredSize(new Dimension(290, 10));

        // Transmisión
        Tarjeta trans = new Tarjeta("Transmisión");
        JPanel t = trans.contenido();
        t.setLayout(new BorderLayout(0, 10));
        btEmisora.setIcon(Iconos.de(Iconos.Tipo.ANTENA, 20, Color.WHITE));
        btEmisora.setPreferredSize(new Dimension(10, 48));
        btEmisora.setMinimumSize(new Dimension(10, 48));
        btEmisora.addActionListener(e -> alternarEmisora());
        t.add(btEmisora, BorderLayout.NORTH);
        lbConexion.setVerticalAlignment(SwingConstants.TOP);
        t.add(lbConexion, BorderLayout.CENTER);
        mostrarDatosConexion();
        col.add(trans, BorderLayout.NORTH);

        // Micrófono
        Tarjeta mic = new Tarjeta("Micrófono del locutor");
        JPanel m = mic.contenido();
        m.setLayout(new GridBagLayout());
        GridBagConstraints c = filaCompleta();
        btMic.setPreferredSize(new Dimension(10, 48));
        btMic.setMinimumSize(new Dimension(10, 48));
        Tema.estiloPrincipal(btMic, new Color(0x3D, 0x38, 0x60));
        btMic.addActionListener(e -> alternarMicrofono());
        m.add(btMic, c);
        c.gridy++;
        c.insets = new Insets(14, 0, 4, 0);
        m.add(Tema.etiqueta("Nivel de tu voz", Font.PLAIN, 12f, Tema.TEXTO_SUAVE), c);
        c.gridy++;
        c.insets = new Insets(0, 0, 0, 0);
        m.add(vuMic, c);
        c.gridy++;
        c.insets = new Insets(12, 0, 0, 0);
        m.add(conIcono(Iconos.Tipo.MIC, slMic), c);
        slMic.addChangeListener(e -> { if (mezclador != null) mezclador.setVolMicrofono(slMic.getValue() / 100f); });
        c.gridy++;
        chAtenuar.addActionListener(e -> { if (mezclador != null) mezclador.setAtenuarAlHablar(chAtenuar.isSelected()); });
        m.add(chAtenuar, c);
        JPanel arriba = new JPanel(new BorderLayout());
        arriba.setOpaque(false);
        arriba.add(mic, BorderLayout.NORTH);
        col.add(arriba, BorderLayout.CENTER);
        return col;
    }

    private JComponent columnaCentral() {
        JPanel col = new JPanel(new BorderLayout(0, 16));
        col.setOpaque(false);

        // Sonando ahora
        Tarjeta ahora = new Tarjeta("Sonando ahora");
        JPanel a = ahora.contenido();
        a.setLayout(new GridBagLayout());
        GridBagConstraints c = filaCompleta();
        a.add(lbSonando, c);
        c.gridy++;
        a.add(lbEstado, c);
        c.gridy++;
        c.insets = new Insets(14, 0, 14, 0);
        ecualizador.setPreferredSize(new Dimension(300, 110));
        a.add(ecualizador, c);
        c.gridy++;
        c.insets = new Insets(0, 0, 0, 0);
        JPanel barra = new JPanel(new BorderLayout(10, 0));
        barra.setOpaque(false);
        progreso.setPreferredSize(new Dimension(10, 6));
        barra.add(progreso, BorderLayout.CENTER);
        barra.add(lbTiempo, BorderLayout.EAST);
        a.add(barra, c);
        c.gridy++;
        c.insets = new Insets(14, 0, 0, 0);
        JPanel controles = new JPanel(new BorderLayout(16, 0));
        controles.setOpaque(false);
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        botones.setOpaque(false);
        btPlay.setPreferredSize(new Dimension(52, 52));
        Tema.estiloPrincipal(btPlay, Tema.ACENTO);
        btPlay.putClientProperty("FlatLaf.style", btPlay.getClientProperty("FlatLaf.style") + ";arc:999");
        btPlay.addActionListener(e -> playPausa());
        btStop.addActionListener(e -> detenerCancion());
        btSiguiente.addActionListener(e -> siguiente());
        botones.add(btPlay);
        botones.add(btStop);
        botones.add(btSiguiente);
        controles.add(botones, BorderLayout.WEST);
        JPanel volumen = new JPanel(new BorderLayout(0, 6));
        volumen.setOpaque(false);
        volumen.setBorder(new EmptyBorder(6, 0, 0, 0));
        volumen.add(conIcono(Iconos.Tipo.PARLANTE, slMusica), BorderLayout.NORTH);
        volumen.add(vuSalida, BorderLayout.CENTER);
        slMusica.addChangeListener(e -> { if (mezclador != null) mezclador.setVolMusica(slMusica.getValue() / 100f); });
        controles.add(volumen, BorderLayout.CENTER);
        a.add(controles, c);
        c.gridy++;
        c.insets = new Insets(8, 0, 0, 0);
        chMonitor.addActionListener(e -> { if (mezclador != null) mezclador.setMonitor(chMonitor.isSelected()); });
        a.add(chMonitor, c);
        col.add(ahora, BorderLayout.NORTH);

        // Lista de reproducción
        Tarjeta tLista = new Tarjeta("Lista de reproducción · solo MP3");
        JPanel l = tLista.contenido();
        l.setLayout(new BorderLayout(0, 10));
        jlLista.setCellRenderer(new RenderCancion());
        jlLista.setOpaque(false);
        jlLista.setBackground(Tema.TARJETA);
        jlLista.setFixedCellHeight(38);
        jlLista.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && jlLista.getSelectedIndex() >= 0) reproducir(jlLista.getSelectedIndex());
            }
        });
        jlLista.setTransferHandler(new SoltarArchivos());
        JScrollPane sp = new JScrollPane(jlLista);
        sp.setBorder(BorderFactory.createLineBorder(Tema.BORDE));
        sp.getViewport().setBackground(Tema.TARJETA);
        l.add(sp, BorderLayout.CENTER);
        JPanel acciones = new JPanel(new BorderLayout());
        acciones.setOpaque(false);
        JPanel izq = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        izq.setOpaque(false);
        JButton btAgregar = new JButton("Agregar MP3", Iconos.de(Iconos.Tipo.MAS, 14, Tema.TEXTO));
        btAgregar.addActionListener(e -> elegirArchivos());
        JButton btQuitar = new JButton("Quitar", Iconos.de(Iconos.Tipo.MENOS, 14, Tema.TEXTO));
        btQuitar.addActionListener(e -> quitarSeleccionadas());
        izq.add(btAgregar);
        izq.add(Box.createHorizontalStrut(8));
        izq.add(btQuitar);
        acciones.add(izq, BorderLayout.WEST);
        acciones.add(Tema.etiqueta("También puedes arrastrar archivos aquí · doble clic para reproducir",
                Font.PLAIN, 11f, Tema.TEXTO_SUAVE), BorderLayout.EAST);
        l.add(acciones, BorderLayout.SOUTH);
        col.add(tLista, BorderLayout.CENTER);
        return col;
    }

    private JComponent columnaDerecha() {
        JPanel col = new JPanel(new BorderLayout(0, 16));
        col.setOpaque(false);
        col.setPreferredSize(new Dimension(270, 10));

        Tarjeta tOy = new Tarjeta("Oyentes conectados");
        JPanel o = tOy.contenido();
        o.setLayout(new BorderLayout(0, 8));
        JPanel conteo = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        conteo.setOpaque(false);
        conteo.add(lbOyentes);
        conteo.add(Tema.etiqueta("escuchando", Font.PLAIN, 13f, Tema.TEXTO_SUAVE));
        o.add(conteo, BorderLayout.NORTH);
        JList<Oyente> jlOyentes = new JList<>(modeloOyentes);
        jlOyentes.setBackground(Tema.TARJETA);
        jlOyentes.setCellRenderer(new RenderOyente());
        jlOyentes.setFixedCellHeight(44);
        JScrollPane sp = new JScrollPane(jlOyentes);
        sp.setBorder(null);
        sp.getViewport().setBackground(Tema.TARJETA);
        o.add(sp, BorderLayout.CENTER);
        col.add(tOy, BorderLayout.CENTER);

        Tarjeta tLog = new Tarjeta("Actividad");
        bitacora.setEditable(false);
        bitacora.setLineWrap(true);
        bitacora.setWrapStyleWord(true);
        bitacora.setBackground(Tema.TARJETA);
        bitacora.setForeground(Tema.TEXTO_SUAVE);
        bitacora.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JScrollPane spLog = new JScrollPane(bitacora);
        spLog.setBorder(null);
        spLog.setPreferredSize(new Dimension(10, 220));
        tLog.contenido().setLayout(new BorderLayout());
        tLog.contenido().add(spLog);
        col.add(tLog, BorderLayout.SOUTH);
        return col;
    }

    private static GridBagConstraints filaCompleta() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        return c;
    }

    private static JComponent conIcono(Iconos.Tipo tipo, JComponent comp) {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setOpaque(false);
        p.add(new JLabel(Iconos.de(tipo, 16, Tema.TEXTO_SUAVE)), BorderLayout.WEST);
        p.add(comp, BorderLayout.CENTER);
        return p;
    }

    private void mostrarDatosConexion() {
        List<String> ips = Red.ipsLocales();
        StringBuilder sb = new StringBuilder("<html>");
        if (servidor.activo()) {
            sb.append("Los oyentes se conectan a:<br>");
            if (ips.isEmpty()) sb.append("<b>127.0.0.1</b> (sin red)<br>");
            for (String ip : ips) sb.append("<b style='color:#ECEAF5;font-size:13px'>").append(ip).append("</b><br>");
            sb.append("Puerto UDP ").append(Protocolo.PUERTO);
        } else {
            sb.append("Inicia la emisora para que los equipos de tu red puedan escucharla.");
        }
        lbConexion.setText(sb.append("</html>").toString());
    }

    // ------------------------------------------------------------------ acciones

    private void alternarEmisora() {
        if (servidor.activo()) {
            apagar();
            registrar("Emisora apagada");
        } else {
            try {
                servidor.iniciar(Protocolo.PUERTO);
            } catch (SocketException e) {
                JOptionPane.showMessageDialog(this, "No se pudo abrir el puerto UDP " + Protocolo.PUERTO
                        + ".\n¿Hay otra emisora abierta en este equipo?\n\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            mezclador = new Mezclador(microfono, servidor::enviarAudio);
            mezclador.setVolMusica(slMusica.getValue() / 100f);
            mezclador.setVolMicrofono(slMic.getValue() / 100f);
            mezclador.setAtenuarAlHablar(chAtenuar.isSelected());
            mezclador.setMonitor(chMonitor.isSelected());
            mezclador.iniciar();
            List<String> ips = Red.ipsLocales();
            registrar("Emisora al aire en " + (ips.isEmpty() ? "127.0.0.1" : String.join(", ", ips))
                    + " : " + Protocolo.PUERTO + " (UDP)");
        }
        actualizarControles();
    }

    private void apagar() {
        if (microfono.activo()) microfono.cerrar();
        btMic.setSelected(false);
        if (mezclador != null) {
            mezclador.setCancion(null, null);
            mezclador.detener();
            mezclador = null;
        }
        indiceActual = -1;
        servidor.detener();
    }

    private void alternarMicrofono() {
        if (btMic.isSelected()) {
            try {
                microfono.abrir();
                registrar("Micrófono abierto: estás al aire");
            } catch (LineUnavailableException | SecurityException | IllegalArgumentException e) {
                btMic.setSelected(false);
                JOptionPane.showMessageDialog(this, "No se pudo abrir el micrófono.\n\n" + e.getMessage(),
                        "Micrófono", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            microfono.cerrar();
            registrar("Micrófono cerrado");
        }
        servidor.enviarInfo();
        actualizarControles();
    }

    private void playPausa() {
        if (mezclador == null) return;
        if (mezclador.cancion() == null) {
            int i = jlLista.getSelectedIndex() >= 0 ? jlLista.getSelectedIndex() : 0;
            if (lista.isEmpty()) {
                elegirArchivos();
                if (lista.isEmpty()) return;
            }
            reproducir(i);
        } else {
            mezclador.setPausado(!mezclador.pausado());
            servidor.enviarInfo();
        }
        actualizarControles();
    }

    private void reproducir(int i) {
        if (mezclador == null) {
            JOptionPane.showMessageDialog(this, "Primero inicia la emisora.", "Emisora apagada", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (i < 0 || i >= lista.size()) return;
        indiceActual = i;
        File f = lista.get(i);
        FuenteMp3 fuente = new FuenteMp3(f);
        mezclador.setPausado(false);
        mezclador.setCancion(fuente, () -> SwingUtilities.invokeLater(() -> cancionTerminada(fuente)));
        jlLista.setSelectedIndex(i);
        registrar("Sonando: " + fuente.titulo());
        servidor.enviarInfo();
        actualizarControles();
    }

    private void cancionTerminada(FuenteMp3 fuente) {
        if (fuente.error() != null) registrar(fuente.error());
        if (mezclador == null || mezclador.cancion() != null) return;
        if (indiceActual + 1 < lista.size()) {
            reproducir(indiceActual + 1);
        } else {
            indiceActual = -1;
            registrar("Fin de la lista de reproducción");
            servidor.enviarInfo();
            actualizarControles();
        }
    }

    private void detenerCancion() {
        if (mezclador == null) return;
        mezclador.setCancion(null, null);
        indiceActual = -1;
        servidor.enviarInfo();
        actualizarControles();
    }

    private void siguiente() {
        if (lista.isEmpty()) return;
        reproducir((indiceActual + 1) % lista.size());
    }

    private void elegirArchivos() {
        JFileChooser fc = new JFileChooser();
        fc.setMultiSelectionEnabled(true);
        fc.setAcceptAllFileFilterUsed(false);
        fc.setFileFilter(new FileNameExtensionFilter("Audio MP3 (*.mp3)", "mp3"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) agregar(List.of(fc.getSelectedFiles()));
    }

    private void agregar(List<File> archivos) {
        List<String> rechazados = new ArrayList<>();
        for (File f : archivos) {
            if (f.isDirectory()) {
                File[] hijos = f.listFiles();
                if (hijos != null) agregar(List.of(hijos));
            } else if (FuenteMp3.esMp3Valido(f)) {
                lista.addElement(f);
            } else {
                rechazados.add(f.getName());
            }
        }
        if (!rechazados.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Solo se aceptan archivos MP3 válidos. No se agregaron:\n• "
                    + String.join("\n• ", rechazados), "Formato no permitido", JOptionPane.WARNING_MESSAGE);
        }
        actualizarControles();
    }

    private void quitarSeleccionadas() {
        int[] sel = jlLista.getSelectedIndices();
        for (int k = sel.length - 1; k >= 0; k--) {
            int i = sel[k];
            if (i == indiceActual) detenerCancion();
            else if (i < indiceActual) indiceActual--;
            lista.remove(i);
        }
        actualizarControles();
    }

    // ------------------------------------------------------------------ estado

    /** Lo que viaja en los mensajes INFO: titulo|microfono. */
    private String infoActual() {
        Mezclador mz = mezclador;
        FuenteMp3 c = mz != null ? mz.cancion() : null;
        String titulo = c == null ? "" : (mz.pausado() ? "(en pausa) " : "") + c.titulo();
        return titulo.replace(Protocolo.SEP, "/") + Protocolo.SEP + (microfono.activo() ? "1" : "0");
    }

    private void actualizarControles() {
        boolean al = servidor.activo();
        btEmisora.setText(al ? "Detener emisora" : "Iniciar emisora");
        Tema.estiloPrincipal(btEmisora, al ? new Color(0x3D, 0x38, 0x60) : Tema.ACENTO);
        btMic.setEnabled(al);
        btMic.setText(microfono.activo() ? "Cerrar micrófono" : "Abrir micrófono");
        btMic.setIcon(Iconos.de(microfono.activo() ? Iconos.Tipo.MIC : Iconos.Tipo.MIC_OFF, 22, Color.WHITE));
        Tema.estiloPrincipal(btMic, microfono.activo() ? Tema.ROJO : new Color(0x3D, 0x38, 0x60));
        btPlay.setEnabled(al);
        btStop.setEnabled(al);
        btSiguiente.setEnabled(al && !lista.isEmpty());

        FuenteMp3 c = mezclador != null ? mezclador.cancion() : null;
        boolean sonando = c != null && !mezclador.pausado();
        btPlay.setIcon(Iconos.de(sonando ? Iconos.Tipo.PAUSA : Iconos.Tipo.PLAY, 20, Color.WHITE));
        lbSonando.setText(c != null ? c.titulo() : (microfono.activo() ? "Micrófono abierto" : "Nada sonando"));
        if (!al) lbEstado.setText("Inicia la emisora para transmitir");
        else if (c != null) lbEstado.setText(mezclador.pausado() ? "En pausa" : "Transmitiendo en MP3 · " + Protocolo.KBPS + " kbps");
        else if (lista.isEmpty()) lbEstado.setText("Agrega canciones MP3 a la lista");
        else lbEstado.setText("Pulsa reproducir o haz doble clic en una canción");

        cabecera.setEnVivo(al, al ? (microfono.activo() ? "EN VIVO · MIC ABIERTO" : "AL AIRE") : "FUERA DEL AIRE");
        cabecera.setSubtitulo(al ? "Transmitiendo por UDP en el puerto " + Protocolo.PUERTO : "Emisora apagada");
        mostrarDatosConexion();
        jlLista.repaint();
    }

    private void refrescarOyentes() {
        SwingUtilities.invokeLater(() -> {
            modeloOyentes.clear();
            for (Oyente o : servidor.oyentes()) modeloOyentes.addElement(o);
            lbOyentes.setText(String.valueOf(modeloOyentes.size()));
        });
    }

    private void animar() {
        Mezclador mz = mezclador;
        vuMic.setNivel(mz != null ? mz.nivelMic() : 0f);
        vuSalida.setNivel(mz != null ? mz.nivelSalida() : 0f);
        ecualizador.setBandas(mz != null ? mz.bandas() : null);
        FuenteMp3 c = mz != null ? mz.cancion() : null;
        if (c != null) {
            long pos = c.posicionMs(), dur = c.duracionMs();
            progreso.setValue(dur > 0 ? (int) Math.min(1000, pos * 1000 / dur) : 0);
            lbTiempo.setText(tiempo(pos) + " / " + tiempo(dur));
        } else {
            progreso.setValue(0);
            lbTiempo.setText("0:00 / 0:00");
        }
    }

    private static String tiempo(long ms) {
        long s = ms / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    private void registrar(String msg) {
        String linea = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "  " + msg;
        System.out.println(msg);
        SwingUtilities.invokeLater(() -> {
            bitacora.append(linea + "\n");
            bitacora.setCaretPosition(bitacora.getDocument().getLength());
        });
    }

    // ------------------------------------------------------------------ renderizadores

    private class RenderCancion extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foco) {
            JLabel c = (JLabel) super.getListCellRendererComponent(l, v, i, sel, foco);
            File f = (File) v;
            boolean actual = i == indiceActual && mezclador != null && mezclador.cancion() != null;
            c.setText(String.format("%02d   %s", i + 1, FuenteMp3.titulo(f)));
            c.setBorder(new EmptyBorder(0, 12, 0, 12));
            c.setIcon(actual ? Iconos.de(Iconos.Tipo.PARLANTE, 16, Tema.ACENTO) : null);
            c.setIconTextGap(10);
            c.setHorizontalTextPosition(SwingConstants.RIGHT);
            if (actual) {
                c.setForeground(Tema.ACENTO);
                c.setFont(c.getFont().deriveFont(Font.BOLD));
            }
            if (!sel) c.setBackground(i % 2 == 0 ? Tema.TARJETA : new Color(0x24, 0x21, 0x35));
            return c;
        }
    }

    private static class RenderOyente extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foco) {
            JLabel c = (JLabel) super.getListCellRendererComponent(l, v, i, sel, foco);
            Oyente o = (Oyente) v;
            c.setText("<html><b>" + escapar(o.nombre()) + "</b><br><span style='color:#9A96B5'>" + o.ip() + "</span></html>");
            c.setIcon(new Avatar(o.nombre()));
            c.setIconTextGap(10);
            c.setBorder(new EmptyBorder(2, 4, 2, 4));
            if (!sel) c.setBackground(Tema.TARJETA);
            return c;
        }
    }

    static String escapar(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Círculo de color con la inicial del oyente. */
    private record Avatar(String nombre) implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float tono = (Math.abs(nombre.hashCode()) % 360) / 360f;
            g2.setColor(Color.getHSBColor(tono, 0.55f, 0.85f));
            g2.fillOval(x, y, 32, 32);
            g2.setColor(Color.WHITE);
            g2.setFont(Tema.fuente(Font.BOLD, 14f));
            String ini = nombre.isEmpty() ? "?" : nombre.substring(0, 1).toUpperCase();
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(ini, x + (32 - fm.stringWidth(ini)) / 2, y + (32 + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 32;
        }

        @Override
        public int getIconHeight() {
            return 32;
        }
    }

    /** Permite arrastrar archivos MP3 a la lista. */
    private class SoltarArchivos extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport s) {
            return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean importData(TransferSupport s) {
            try {
                agregar((List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor));
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Tema.aplicar();
            new EmisoraSvr().setVisible(true);
        });
    }
}
