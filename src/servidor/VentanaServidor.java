package servidor;

import comun.EstadoPresentacion;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * Vista del servidor: muestra quien esta conectado, la bitacora de lo que hace
 * cada cliente y el estado de la proyeccion. Implementa VistaServidor, que es
 * el unico punto por donde la logica habla con Swing.
 */
public class VentanaServidor implements VistaServidor {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JFrame ventana = new JFrame("Servidor de presentaciones (RMI)");
    private final DefaultListModel<Sesion> modeloSesiones = new DefaultListModel<>();
    private final JList<Sesion> listaSesiones = new JList<>(modeloSesiones);
    private final JTextArea bitacora = new JTextArea();
    private final JLabel lblEstado = new JLabel("Sin presentacion abierta");
    private final JLabel lblConexion = new JLabel(" ");
    private final JButton btnDetener = new JButton("Detener proyeccion");
    private final VentanaProyeccion proyeccion;

    private ServidorPresentacionImpl servidor;

    public VentanaServidor() {
        proyeccion = new VentanaProyeccion(this::detenerProyeccion);
        construir();
    }

    /** Se inyecta despues porque el servidor necesita esta vista en su constructor. */
    public void setServidor(ServidorPresentacionImpl servidor) {
        this.servidor = servidor;
    }

    private void construir() {
        ventana.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        ventana.setSize(880, 560);
        ventana.setLocationRelativeTo(null);

        lblEstado.setFont(lblEstado.getFont().deriveFont(Font.BOLD, 16f));
        lblEstado.setBorder(BorderFactory.createEmptyBorder(12, 14, 4, 14));
        lblConexion.setForeground(new Color(90, 90, 90));
        lblConexion.setBorder(BorderFactory.createEmptyBorder(0, 14, 10, 14));

        JPanel cabecera = new JPanel(new BorderLayout());
        cabecera.add(lblEstado, BorderLayout.NORTH);
        cabecera.add(lblConexion, BorderLayout.SOUTH);

        JPanel panelClientes = new JPanel(new BorderLayout());
        panelClientes.setBorder(BorderFactory.createTitledBorder("Clientes conectados"));
        panelClientes.add(new JScrollPane(listaSesiones), BorderLayout.CENTER);
        panelClientes.setPreferredSize(new Dimension(320, 0));

        bitacora.setEditable(false);
        bitacora.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JPanel panelBitacora = new JPanel(new BorderLayout());
        panelBitacora.setBorder(BorderFactory.createTitledBorder("Bitacora"));
        panelBitacora.add(new JScrollPane(bitacora), BorderLayout.CENTER);

        JSplitPane division = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, panelClientes, panelBitacora);
        division.setResizeWeight(0.35);

        JPanel pie = new JPanel(new BorderLayout());
        pie.setBorder(BorderFactory.createEmptyBorder(8, 14, 12, 14));
        pie.add(btnDetener, BorderLayout.EAST);

        ventana.setLayout(new BorderLayout());
        ventana.add(cabecera, BorderLayout.NORTH);
        ventana.add(division, BorderLayout.CENTER);
        ventana.add(pie, BorderLayout.SOUTH);

        btnDetener.addActionListener(e -> detenerProyeccion());
        btnDetener.setEnabled(false);
    }

    public void mostrar(String textoConexion) {
        lblConexion.setText(textoConexion);
        ventana.setVisible(true);
    }

    private void detenerProyeccion() {
        if (servidor != null) {
            servidor.terminarDesdeServidor();
        }
    }

    // ================= VistaServidor =================

    @Override
    public boolean autorizar(String usuario, String host) {
        // Llega desde un hilo del pool de RMI: hay que saltar al EDT y esperar.
        final boolean[] respuesta = new boolean[1];
        Runnable tarea = () -> respuesta[0] = DialogoAutorizacion.preguntar(
                ventanaSuperior(), usuario, host);
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                tarea.run();
            } else {
                SwingUtilities.invokeAndWait(tarea);
            }
        } catch (Exception e) {
            registrar("Error mostrando el dialogo de autorizacion: " + e.getMessage());
            return false;
        }
        return respuesta[0];
    }

    private Window ventanaSuperior() {
        return ventana.isVisible() ? ventana : null;
    }

    @Override
    public void estadoCambiado(EstadoPresentacion estado, Path archivo) {
        SwingUtilities.invokeLater(() -> {
            lblEstado.setText(estado.descripcionCorta()
                    + (estado.isEnPresentacion() ? "   [PROYECTANDO]" : "   [detenida]"));
            btnDetener.setEnabled(estado.isEnPresentacion());
            if (estado.isEnPresentacion()) {
                proyeccion.mostrar(archivo, estado.descripcionCorta());
            } else {
                proyeccion.ocultar();
            }
        });
    }

    @Override
    public void sesionesActualizadas(List<Sesion> sesiones) {
        SwingUtilities.invokeLater(() -> {
            modeloSesiones.clear();
            for (Sesion s : sesiones) {
                modeloSesiones.addElement(s);
            }
            ventana.setTitle("Servidor de presentaciones (RMI) - "
                    + sesiones.size() + " cliente(s) conectado(s)");
        });
    }

    @Override
    public void registrar(String mensaje) {
        String linea = "[" + LocalTime.now().format(HORA) + "] " + mensaje;
        SwingUtilities.invokeLater(() -> {
            bitacora.append(linea + System.lineSeparator());
            bitacora.setCaretPosition(bitacora.getDocument().getLength());
        });
        System.out.println(linea);
    }
}