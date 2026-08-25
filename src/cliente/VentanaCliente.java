package cliente;

import comun.ControlPresentacion;
import comun.EstadoPresentacion;
import comun.InfoPresentacion;
import comun.SesionInvalidaException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.rmi.RemoteException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;

/**
 * Control remoto. No muestra las diapositivas: es un mando con los botones de
 * navegacion, el salto directo y una referencia textual de donde va la
 * proyeccion.
 *
 * Todas las llamadas remotas salen por un unico hilo de fondo, nunca por el
 * EDT: si la red se pone lenta, la interfaz no se congela.
 */
public class VentanaCliente {

    private static final Color GRIS = new Color(120, 120, 120);
    private static final Color AVISO = new Color(196, 120, 20);

    private final ControlPresentacion servidor;
    private final ObservadorPresentacionImpl observador;
    private final String token;
    private final String usuario;

    private final JFrame ventana = new JFrame();
    private final JComboBox<InfoPresentacion> cmbPresentaciones = new JComboBox<>();
    private final JButton btnActualizar = new JButton("\u21bb");
    private final JLabel lblNumero = new JLabel("-", SwingConstants.CENTER);
    private final JLabel lblArchivo = new JLabel(" ", SwingConstants.CENTER);
    private final JButton btnAtras = new JButton("\u25c0");
    private final JButton btnSiguiente = new JButton("\u25b6");
    private final JButton btnProyectar = new JButton("Presentar");
    private final JSpinner spnIrA = new JSpinner(new SpinnerNumberModel(1, 1, 1, 1));
    private final JButton btnIrA = new JButton("Ir");
    private final JLabel lblAviso = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel lblPie = new JLabel(" ", SwingConstants.CENTER);
    private final Timer temporizadorAviso = new Timer(3000, e -> lblAviso.setText(" "));

    private final ExecutorService remoto = Executors.newSingleThreadExecutor(tarea -> {
        Thread t = new Thread(tarea, "llamadas-rmi");
        t.setDaemon(true);
        return t;
    });

    private volatile EstadoPresentacion estado;
    private volatile boolean cerrando;
    /** Evita que refrescar la lista dispare una apertura por si sola. */
    private boolean actualizandoLista;

    public VentanaCliente(ControlPresentacion servidor, ObservadorPresentacionImpl observador,
                        String token, String usuario, EstadoPresentacion inicial) {
        this.servidor = servidor;
        this.observador = observador;
        this.token = token;
        this.usuario = usuario;
        this.estado = inicial;

        observador.setAlCambiarEstado(this::recibirEstado);

        construir();
        aplicarEstado(inicial);
        actualizarListaPresentaciones();
    }

    // ================= construccion de la vista =================

    private void construir() {
        ventana.setTitle("Control - " + usuario);
        ventana.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        ventana.setSize(380, 540);
        ventana.setMinimumSize(new Dimension(340, 480));
        ventana.setLocationByPlatform(true);
        ventana.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cerrar();
            }
        });

        JPanel raiz = new JPanel(new BorderLayout(0, 10));
        raiz.setBorder(BorderFactory.createEmptyBorder(10, 14, 12, 14));
        raiz.add(barraPresentacion(), BorderLayout.NORTH);
        raiz.add(mando(), BorderLayout.CENTER);
        raiz.add(pie(), BorderLayout.SOUTH);
        ventana.setContentPane(raiz);

        atajosDeTeclado(raiz);

        btnActualizar.addActionListener(e -> actualizarListaPresentaciones());
        cmbPresentaciones.addActionListener(e -> {
            if (!actualizandoLista) {
                abrirSeleccionada();
            }
        });
        btnSiguiente.addActionListener(e -> siguiente());
        btnAtras.addActionListener(e -> atras());
        btnProyectar.addActionListener(e -> alternarProyeccion());
        btnIrA.addActionListener(e -> irA());
    }

    /** Barra compacta: elegir presentacion no es la accion principal. */
    private JPanel barraPresentacion() {
        btnActualizar.setToolTipText("Volver a leer las presentaciones del servidor");
        btnActualizar.setPreferredSize(new Dimension(44, 26));
        btnActualizar.setFocusable(false);
        cmbPresentaciones.setFocusable(false);

        JPanel barra = new JPanel(new BorderLayout(6, 0));
        barra.add(cmbPresentaciones, BorderLayout.CENTER);
        barra.add(btnActualizar, BorderLayout.EAST);
        return barra;
    }

    private JPanel mando() {
        lblNumero.setFont(lblNumero.getFont().deriveFont(Font.BOLD, 58f));
        lblArchivo.setFont(lblArchivo.getFont().deriveFont(Font.PLAIN, 12f));
        lblArchivo.setForeground(GRIS);
        lblAviso.setFont(lblAviso.getFont().deriveFont(Font.PLAIN, 11f));
        lblAviso.setForeground(AVISO);

        JPanel donde = new JPanel(new BorderLayout());
        donde.add(lblNumero, BorderLayout.CENTER);
        donde.add(lblArchivo, BorderLayout.SOUTH);

        Font flecha = btnAtras.getFont().deriveFont(Font.PLAIN, 26f);
        btnAtras.setFont(flecha);
        btnSiguiente.setFont(flecha);
        btnAtras.setFocusable(false);
        btnSiguiente.setFocusable(false);
        JPanel flechas = new JPanel(new GridLayout(1, 2, 10, 0));
        flechas.add(btnAtras);
        flechas.add(btnSiguiente);
        flechas.setPreferredSize(new Dimension(0, 96));

        JPanel salto = new JPanel(new BorderLayout(6, 0));
        JPanel campo = new JPanel(new BorderLayout(6, 0));
        JLabel etq = new JLabel("Ir a:");
        spnIrA.setPreferredSize(new Dimension(64, 28));
        campo.add(etq, BorderLayout.WEST);
        campo.add(spnIrA, BorderLayout.CENTER);
        btnIrA.setPreferredSize(new Dimension(60, 28));
        btnIrA.setFocusable(false);
        salto.add(campo, BorderLayout.CENTER);
        salto.add(btnIrA, BorderLayout.EAST);

        btnProyectar.setPreferredSize(new Dimension(0, 40));
        btnProyectar.setFocusable(false);

        JPanel inferior = new JPanel(new BorderLayout(0, 8));
        inferior.add(salto, BorderLayout.NORTH);
        inferior.add(btnProyectar, BorderLayout.CENTER);
        inferior.add(lblAviso, BorderLayout.SOUTH);

        JPanel centro = new JPanel(new BorderLayout(0, 12));
        centro.add(donde, BorderLayout.CENTER);
        JPanel abajo = new JPanel(new BorderLayout(0, 10));
        abajo.add(flechas, BorderLayout.NORTH);
        abajo.add(inferior, BorderLayout.CENTER);
        centro.add(abajo, BorderLayout.SOUTH);
        return centro;
    }

    private JLabel pie() {
        lblPie.setFont(lblPie.getFont().deriveFont(Font.PLAIN, 10f));
        lblPie.setForeground(GRIS);
        return lblPie;
    }

    /** Un control se maneja con el teclado, no solo con el raton. */
    private void atajosDeTeclado(JComponent raiz) {
        accion(raiz, "RIGHT", e -> siguiente());
        accion(raiz, "PAGE_DOWN", e -> siguiente());
        accion(raiz, "SPACE", e -> siguiente());
        accion(raiz, "LEFT", e -> atras());
        accion(raiz, "PAGE_UP", e -> atras());
        accion(raiz, "F5", e -> alternarProyeccion());
    }

    private void accion(JComponent raiz, String tecla, ActionListener accion) {
        raiz.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(tecla), tecla);
        raiz.getActionMap().put(tecla, new javax.swing.AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                accion.actionPerformed(e);
            }
        });
    }

    public void mostrar() {
        ventana.setVisible(true);
    }

    // ================= acciones =================

    private void siguiente() {
        if (btnSiguiente.isEnabled()) {
            enviar("siguiente", () -> servidor.siguiente(token, estado.getVersion()));
        }
    }

    private void atras() {
        if (btnAtras.isEnabled()) {
            enviar("atras", () -> servidor.anterior(token, estado.getVersion()));
        }
    }

    private void irA() {
        if (!btnIrA.isEnabled()) {
            return;
        }
        int destino = ((Number) spnIrA.getValue()).intValue() - 1;
        enviar("ir a " + (destino + 1), () -> servidor.irA(token, destino));
    }

    /** Un solo boton para las dos caras de la proyeccion. */
    private void alternarProyeccion() {
        if (!btnProyectar.isEnabled()) {
            return;
        }
        if (estado.isEnPresentacion()) {
            enviar("terminar", () -> servidor.terminarPresentacion(token));
        } else {
            enviar("presentar", () -> servidor.presentar(token));
        }
    }

    private void abrirSeleccionada() {
        InfoPresentacion seleccion = (InfoPresentacion) cmbPresentaciones.getSelectedItem();
        if (seleccion == null) {
            return;
        }
        enviar("abrir", () -> servidor.abrirPresentacion(token, seleccion.getNombre()));
    }

    // ================= envio de ordenes =================

    /** Una orden remota cualquiera. */
    private interface Operacion {
        EstadoPresentacion ejecutar() throws RemoteException, SesionInvalidaException;
    }

    /**
     * Manda una orden en el hilo de fondo y compara la version que vuelve con
     * la que habia antes: si no cambio, el servidor la ignoro y se avisa.
     */
    private void enviar(String etiqueta, Operacion operacion) {
        long versionAntes = estado.getVersion();

        remoto.submit(() -> {
            try {
                EstadoPresentacion resultado = operacion.ejecutar();
                if (resultado.getVersion() == versionAntes) {
                    avisar("'" + etiqueta + "' sin efecto: el servidor la ignoro");
                }
                recibirEstado(resultado);
            } catch (SesionInvalidaException e) {
                sesionPerdida(e.getMessage());
            } catch (RemoteException e) {
                avisar("Sin comunicacion con el servidor");
            }
        });
    }

    private void actualizarListaPresentaciones() {
        remoto.submit(() -> {
            try {
                List<InfoPresentacion> lista = servidor.listarPresentaciones(token);
                SwingUtilities.invokeLater(() -> {
                    actualizandoLista = true;
                    try {
                        cmbPresentaciones.setModel(
                                new DefaultComboBoxModel<>(lista.toArray(new InfoPresentacion[0])));
                        seleccionarActual(lista);
                    } finally {
                        actualizandoLista = false;
                    }
                });
            } catch (SesionInvalidaException e) {
                sesionPerdida(e.getMessage());
            } catch (RemoteException e) {
                avisar("No se pudo pedir la lista de presentaciones");
            }
        });
    }

    /** Deja marcada en la lista la presentacion que el servidor tiene abierta. */
    private void seleccionarActual(List<InfoPresentacion> lista) {
        String abierta = estado.getPresentacion();
        if (abierta == null) {
            cmbPresentaciones.setSelectedIndex(-1);
            return;
        }
        for (InfoPresentacion info : lista) {
            if (info.getNombre().equals(abierta)) {
                cmbPresentaciones.setSelectedItem(info);
                return;
            }
        }
    }

    // ================= recepcion de cambios =================

    private void recibirEstado(EstadoPresentacion nuevo) {
        // Puede llegar por callback del servidor o como retorno de una orden.
        // Se descarta lo viejo por si se cruzan en el camino.
        if (nuevo.getVersion() < estado.getVersion()) {
            return;
        }
        estado = nuevo;
        SwingUtilities.invokeLater(() -> aplicarEstado(nuevo));
    }

    private void aplicarEstado(EstadoPresentacion e) {
        boolean hay = e.hayDiapositivas();

        lblNumero.setText(hay ? (e.getIndice() + 1) + " / " + e.getTotal() : "-");
        if (e.getPresentacion() == null) {
            lblArchivo.setText("Elija una presentacion arriba");
        } else if (!hay) {
            lblArchivo.setText("La carpeta no tiene imagenes");
        } else {
            lblArchivo.setText(e.getNombreDiapositiva());
        }

        btnAtras.setEnabled(hay && e.getIndice() > 0);
        btnSiguiente.setEnabled(hay && e.getIndice() < e.getTotal() - 1);
        btnIrA.setEnabled(hay);
        spnIrA.setEnabled(hay);
        btnProyectar.setEnabled(hay);
        btnProyectar.setText(e.isEnPresentacion() ? "Terminar" : "Presentar");

        int total = Math.max(e.getTotal(), 1);
        SpinnerNumberModel modelo = (SpinnerNumberModel) spnIrA.getModel();
        modelo.setMaximum(total);
        if (((Number) spnIrA.getValue()).intValue() > total) {
            spnIrA.setValue(total);
        }
        if (hay) {
            spnIrA.setValue(e.getIndice() + 1);
        }

        lblPie.setText((e.isEnPresentacion() ? "proyectando" : "detenida")
                + "   \u00b7   version " + e.getVersion()
                + "   \u00b7   " + usuario);
    }

    /** Mensaje breve que se borra solo: reemplaza a la bitacora. */
    private void avisar(String mensaje) {
        SwingUtilities.invokeLater(() -> {
            lblAviso.setText(mensaje);
            temporizadorAviso.setRepeats(false);
            temporizadorAviso.restart();
        });
    }

    private void sesionPerdida(String motivo) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(ventana,
                    motivo + "\n\nEl cliente se cerrara; al abrirlo de nuevo debera validarse.",
                    "Sesion no valida", JOptionPane.WARNING_MESSAGE);
            terminarProceso();
        });
    }

    // ================= cierre =================

    private void cerrar() {
        if (cerrando) {
            return;
        }
        cerrando = true;
        remoto.submit(() -> {
            try {
                servidor.desconectar(token);
            } catch (RemoteException ignorada) {
                // el servidor ya no esta; su vigilante retirara la sesion
            }
            SwingUtilities.invokeLater(this::terminarProceso);
        });
    }

    private void terminarProceso() {
        ventana.dispose();
        observador.cerrar();
        remoto.shutdownNow();
        System.exit(0);
    }
}