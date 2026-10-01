package org.gameshakers.cliente;

import org.gameshakers.comun.Protocolo;
import org.gameshakers.comun.ui.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.Map;

/** Ventana del oyente: se conecta a la emisora y reproduce lo que transmite. */
public class CliOyente extends JFrame implements ClienteUdp.Eventos {

    private ClienteUdp cliente;
    private Reproductor reproductor;

    private final Cabecera cabecera = new Cabecera(Protocolo.NOMBRE_EMISORA, "Oyente · sin conectar");
    private final JTextField txNombre = new JTextField();
    private final JComboBox<String> cbIp = new JComboBox<>();
    private final JButton btBuscar = new JButton("Buscar", Iconos.de(Iconos.Tipo.BUSCAR, 14, Tema.TEXTO));
    private final JButton btConectar = Tema.botonPrincipal("Conectar", Tema.ACENTO);

    private final Ecualizador ecualizador = new Ecualizador(Analizador.FRECUENCIAS.length);
    private final JLabel lbTitulo = Tema.etiqueta("Sin conexión", Font.BOLD, 19f, Tema.TEXTO);
    private final JLabel lbMic = Tema.etiqueta("  El locutor está hablando", Font.BOLD, 12f, Color.WHITE);
    private final JLabel lbOyentes = Tema.etiqueta("", Font.PLAIN, 12f, Tema.TEXTO_SUAVE);

    private final JToggleButton btSilencio = new JToggleButton(Iconos.de(Iconos.Tipo.PARLANTE, 18, Tema.TEXTO));
    private final JSlider slVolumen = new JSlider(0, 100, 90);
    private final VuMetro vu = new VuMetro();
    private final JLabel lbEstadisticas = Tema.etiqueta(" ", Font.PLAIN, 11f, Tema.TEXTO_SUAVE);
    private final JLabel lbEstado = Tema.etiqueta("Escribe tu nombre y la IP de la emisora.", Font.PLAIN, 12f, Tema.TEXTO_SUAVE);

    public CliOyente(String ipInicial) {
        super(Protocolo.NOMBRE_EMISORA + " · Oyente");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (cliente != null) cliente.desconectar();
                dispose();
                System.exit(0);
            }
        });
        // si el programa se cierra normalmente (no a la fuerza) también se avisa a la emisora
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { if (cliente != null) cliente.desconectar(); }));

        txNombre.setText(System.getProperty("user.name", ""));
        cbIp.setEditable(true);
        cbIp.addItem(ipInicial != null ? ipInicial : "127.0.0.1");
        construir();
        actualizarControles();
        new Timer(40, e -> animar()).start();
        setMinimumSize(new Dimension(480, 680));
        setSize(540, 760);
        setLocationRelativeTo(null);
    }

    private void construir() {
        JPanel raiz = new JPanel(new BorderLayout());
        raiz.setBackground(Tema.FONDO);
        raiz.add(cabecera, BorderLayout.NORTH);

        JPanel cuerpo = new JPanel(new GridBagLayout());
        cuerpo.setOpaque(false);
        cuerpo.setBorder(new EmptyBorder(16, 16, 12, 16));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(0, 0, 16, 0);

        // Conexión
        Tarjeta con = new Tarjeta("Conexión");
        JPanel k = con.contenido();
        k.setLayout(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(0, 0, 8, 10);
        g.gridy = 0;
        g.gridx = 0;
        k.add(Tema.etiqueta("Tu nombre", Font.PLAIN, 12f, Tema.TEXTO_SUAVE), g);
        g.gridx = 1;
        g.weightx = 1;
        g.gridwidth = 2;
        g.insets = new Insets(0, 0, 8, 0);
        txNombre.putClientProperty("JTextField.placeholderText", "¿Cómo te llamas?");
        k.add(txNombre, g);
        g.gridy = 1;
        g.gridx = 0;
        g.weightx = 0;
        g.gridwidth = 1;
        g.insets = new Insets(0, 0, 12, 10);
        k.add(Tema.etiqueta("IP de la emisora", Font.PLAIN, 12f, Tema.TEXTO_SUAVE), g);
        g.gridx = 1;
        g.weightx = 1;
        k.add(cbIp, g);
        g.gridx = 2;
        g.weightx = 0;
        g.insets = new Insets(0, 0, 12, 0);
        btBuscar.setToolTipText("Buscar emisoras en la red local");
        btBuscar.addActionListener(e -> buscar());
        k.add(btBuscar, g);
        g.gridy = 2;
        g.gridx = 0;
        g.gridwidth = 3;
        g.insets = new Insets(0, 0, 0, 0);
        btConectar.setPreferredSize(new Dimension(10, 46));
        btConectar.setMinimumSize(new Dimension(10, 46));
        btConectar.addActionListener(e -> alternarConexion());
        k.add(btConectar, g);
        c.gridy = 0;
        c.weighty = 0;
        cuerpo.add(con, c);

        // Sonando ahora
        Tarjeta ahora = new Tarjeta("Sonando ahora");
        JPanel a = ahora.contenido();
        a.setLayout(new BorderLayout(0, 12));
        a.add(ecualizador, BorderLayout.CENTER);
        JPanel textos = new JPanel(new GridLayout(0, 1, 0, 6));
        textos.setOpaque(false);
        textos.add(lbTitulo);
        JPanel fila = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        fila.setOpaque(false);
        lbMic.setIcon(Iconos.de(Iconos.Tipo.MIC, 14, Color.WHITE));
        lbMic.setOpaque(true);
        lbMic.setBackground(Tema.ROJO);
        lbMic.setBorder(new EmptyBorder(4, 10, 4, 12));
        lbMic.putClientProperty("FlatLaf.style", "arc:999");
        fila.add(lbMic);
        fila.add(Box.createHorizontalStrut(12));
        fila.add(lbOyentes);
        textos.add(fila);
        a.add(textos, BorderLayout.SOUTH);
        c.gridy = 1;
        c.weighty = 1;
        cuerpo.add(ahora, c);

        // Volumen
        Tarjeta vol = new Tarjeta("Volumen");
        JPanel v = vol.contenido();
        v.setLayout(new BorderLayout(10, 8));
        btSilencio.putClientProperty("JButton.buttonType", "roundRect");
        btSilencio.setToolTipText("Silenciar");
        btSilencio.setFocusPainted(false);
        btSilencio.addActionListener(e -> {
            btSilencio.setIcon(Iconos.de(btSilencio.isSelected() ? Iconos.Tipo.SILENCIO : Iconos.Tipo.PARLANTE, 18, Tema.TEXTO));
            if (reproductor != null) reproductor.setSilencio(btSilencio.isSelected());
        });
        slVolumen.addChangeListener(e -> { if (reproductor != null) reproductor.setVolumen(slVolumen.getValue() / 100f); });
        v.add(btSilencio, BorderLayout.WEST);
        v.add(slVolumen, BorderLayout.CENTER);
        JPanel abajo = new JPanel(new GridLayout(2, 1, 0, 6));
        abajo.setOpaque(false);
        abajo.add(vu);
        abajo.add(lbEstadisticas);
        v.add(abajo, BorderLayout.SOUTH);
        c.gridy = 2;
        c.weighty = 0;
        c.insets = new Insets(0, 0, 10, 0);
        cuerpo.add(vol, c);

        c.gridy = 3;
        c.insets = new Insets(0, 4, 0, 0);
        lbEstado.setPreferredSize(new Dimension(10, 18));   // un mensaje largo no debe ensanchar la ventana
        lbEstado.addPropertyChangeListener("text", e -> lbEstado.setToolTipText(lbEstado.getText()));
        cuerpo.add(lbEstado, c);

        raiz.add(cuerpo, BorderLayout.CENTER);
        setContentPane(raiz);
    }

    // ------------------------------------------------------------------ acciones

    private void alternarConexion() {
        if (cliente != null && cliente.activo()) {
            cliente.desconectar();
            return;
        }
        String nombre = txNombre.getText().strip();
        String ip = String.valueOf(cbIp.getEditor().getItem()).strip();
        if (ip.contains(" ")) ip = ip.substring(0, ip.indexOf(' '));     // "192.168.1.5  (GameShakers FM)"
        if (nombre.isEmpty()) {
            lbEstado.setText("Escribe tu nombre para conectarte.");
            txNombre.requestFocus();
            return;
        }
        if (ip.isEmpty()) {
            lbEstado.setText("Escribe la IP de la emisora o pulsa Buscar.");
            return;
        }
        reproductor = new Reproductor();
        reproductor.setVolumen(slVolumen.getValue() / 100f);
        reproductor.setSilencio(btSilencio.isSelected());
        cliente = new ClienteUdp(this, reproductor);
        try {
            cliente.conectar(ip, nombre);
        } catch (IOException e) {
            reproductor.detener();
            cliente = null;
            lbEstado.setText("Dirección no válida: " + e.getMessage());
            return;
        }
        lbEstado.setText("Conectando con " + ip + "…");
        actualizarControles();
    }

    private void buscar() {
        btBuscar.setEnabled(false);
        lbEstado.setText("Buscando emisoras en la red…");
        new SwingWorker<Map<String, String>, Void>() {
            @Override
            protected Map<String, String> doInBackground() {
                return ClienteUdp.buscarEmisoras(1500);
            }

            @Override
            protected void done() {
                btBuscar.setEnabled(true);
                Map<String, String> res;
                try {
                    res = get();
                } catch (Exception e) {
                    res = Map.of();
                }
                if (res.isEmpty()) {
                    lbEstado.setText("No se encontraron emisoras. Escribe la IP a mano (puede bloquearlo el firewall).");
                    return;
                }
                cbIp.removeAllItems();
                res.forEach((ip, nombre) -> cbIp.addItem(ip + "  (" + nombre + ")"));
                lbEstado.setText("Se encontraron " + res.size() + " emisora(s). Elige una y pulsa Conectar.");
            }
        }.execute();
    }

    // ------------------------------------------------------------------ eventos de la conexión

    @Override
    public void conectado(String nombreEmisora) {
        SwingUtilities.invokeLater(() -> {
            cabecera.setTitulo(nombreEmisora);
            lbEstado.setText("Conectado. ¡Disfruta la emisora!");
            actualizarControles();
        });
    }

    @Override
    public void info(String titulo, boolean microfono, int oyentes) {
        SwingUtilities.invokeLater(() -> {
            lbTitulo.setText(titulo.isEmpty() ? (microfono ? "Al aire con el locutor" : "Emisora en silencio") : titulo);
            lbMic.setVisible(microfono);
            lbOyentes.setText(oyentes == 1 ? "1 oyente conectado" : oyentes + " oyentes conectados");
        });
    }

    @Override
    public void desconectado(String motivo) {
        Reproductor r = reproductor;
        if (r != null) r.detener();
        System.out.println(motivo != null ? motivo : "Desconectado");
        SwingUtilities.invokeLater(() -> {
            lbEstado.setText(motivo != null ? motivo : "Te desconectaste de la emisora.");
            lbEstado.setForeground(motivo != null ? Tema.AMARILLO : Tema.TEXTO_SUAVE);
            lbTitulo.setText("Sin conexión");
            lbOyentes.setText("");
            actualizarControles();
        });
    }

    // ------------------------------------------------------------------ estado

    private void actualizarControles() {
        boolean activo = cliente != null && cliente.activo();
        boolean conectado = activo && !lbEstado.getText().startsWith("Conectando");
        btConectar.setText(activo ? "Desconectar" : "Conectar");
        Tema.estiloPrincipal(btConectar, activo ? new Color(0x3D, 0x38, 0x60) : Tema.ACENTO);
        txNombre.setEnabled(!activo);
        cbIp.setEnabled(!activo);
        btBuscar.setEnabled(!activo);
        if (!activo) lbMic.setVisible(false);
        if (activo) lbEstado.setForeground(Tema.TEXTO_SUAVE);
        cabecera.setEnVivo(conectado, conectado ? "ESCUCHANDO" : (activo ? "CONECTANDO…" : "SIN CONEXIÓN"));
        cabecera.setSubtitulo(activo ? "Oyente · " + txNombre.getText().strip() : "Oyente · sin conectar");
    }

    private void animar() {
        Reproductor r = reproductor;
        ClienteUdp c = cliente;
        boolean activo = c != null && c.activo();
        vu.setNivel(activo ? r.nivel() : 0f);
        ecualizador.setBandas(activo ? r.bandas() : null);
        if (activo) {
            String estado = r.cargando() ? "Cargando buffer…" : "Buffer " + r.bufferMs() + " ms";
            lbEstadisticas.setText(estado + "   ·   Paquetes recibidos " + c.recibidos() + "   ·   Perdidos " + c.perdidos());
            if (r.error() != null && !lbEstado.getText().equals(r.error())) lbEstado.setText(r.error());
        } else {
            lbEstadisticas.setText(" ");
        }
    }

    public static void main(String[] args) {
        String ip = args.length > 0 ? args[0] : null;
        SwingUtilities.invokeLater(() -> {
            Tema.aplicar();
            new CliOyente(ip).setVisible(true);
        });
    }
}
