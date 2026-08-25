package cliente;

import comun.ConexionRechazadaException;
import comun.ControlPresentacion;
import comun.Red;
import comun.ResultadoConexion;
import java.awt.Dialog;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Arranque del cliente.
 *
 * Uso:  java cliente.MainCliente [hostServidor] [puertoRegistro] [ipLocal] [puertoCallback]
 *
 * Cada vez que se abre pide autorizacion al servidor: no se guarda nada de
 * conexiones anteriores, asi que cerrar y volver a abrir obliga a validarse
 * otra vez, que es justo lo que se pedia.
 */
public final class MainCliente {

    private static final int PUERTO_CALLBACK_BASE = 1102;

    private static final int PUERTOS_A_PROBAR = 20;

    private MainCliente() {
    }

    public static void main(String[] args) {
        String hostPorDefecto = (args.length > 0) ? args[0] : "localhost";
        int puerto = (args.length > 1) ? Integer.parseInt(args[1]) : 1099;
        int puertoCallback = (args.length > 2) ? Integer.parseInt(args[2]) : PUERTO_CALLBACK_BASE;

        // El cliente tambien exporta un objeto remoto, asi que tambien necesita
        // anunciar una IP que el servidor pueda alcanzar. Se detecta sola.
        Red.configurarHostname();

        // El cliente tambien exporta un objeto remoto, asi que tambien necesita
        // anunciar una IP alcanzable desde el servidor.
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignorada) {
            // aspecto por defecto
        }

        SwingUtilities.invokeLater(() -> iniciar(hostPorDefecto, puerto, puertoCallback));
    }

        /**
     * Exporta el observador en el primer puerto libre a partir del indicado.
     * Asi se pueden abrir varias instancias del cliente en la misma maquina sin
     * que la segunda choque con "address already in use".
     */
    private static ObservadorPresentacionImpl exportarObservador(int base) throws RemoteException {
        for (int puerto = base; puerto < base + PUERTOS_A_PROBAR; puerto++) {
            try {
                return new ObservadorPresentacionImpl(puerto);
            } catch (RemoteException e) {
                // puerto ocupado por otra instancia: se prueba el siguiente
            }
        }
        // Ultimo recurso: que el sistema elija uno cualquiera.
        return new ObservadorPresentacionImpl(0);
    }

    private static void iniciar(String hostPorDefecto, int puerto, int puertoCallback) {
        String host = (String) JOptionPane.showInputDialog(null,
                "Direccion del servidor (host o IP):", "Conectar",
                JOptionPane.QUESTION_MESSAGE, null, null, hostPorDefecto);
        if (host == null || host.isBlank()) {
            System.exit(0);
        }

        String usuario = (String) JOptionPane.showInputDialog(null,
                "Su nombre de usuario:", "Identificacion",
                JOptionPane.QUESTION_MESSAGE, null, null, System.getProperty("user.name"));
        if (usuario == null || usuario.isBlank()) {
            System.exit(0);
        }

        JDialog espera = crearDialogoEspera(host);

        new Thread(() -> {
            try {
                Registry registro = LocateRegistry.getRegistry(host.trim(), puerto);
                ControlPresentacion servidor =
                        (ControlPresentacion) registro.lookup(ControlPresentacion.NOMBRE_SERVICIO);
                
                ObservadorPresentacionImpl observador = exportarObservador(puertoCallback);

                // Bloquea hasta que el operador del servidor acepte o rechace.
                ResultadoConexion resultado = servidor.solicitarConexion(usuario.trim(), observador);

                SwingUtilities.invokeLater(() -> {
                    espera.dispose();
                    new VentanaCliente(servidor, observador, resultado.getToken(),
                            usuario.trim(), resultado.getEstado()).mostrar();
                });

            } catch (ConexionRechazadaException e) {
                terminarConMensaje(espera,
                        "El servidor rechazo la conexion.\n\n" + e.getMessage(),
                        "Conexion rechazada", JOptionPane.WARNING_MESSAGE);
            } catch (Exception e) {
                terminarConMensaje(espera,
                        "No se pudo conectar con el servidor en " + host + ":" + puerto + ".\n\n"
                                + e.getClass().getSimpleName() + ": " + e.getMessage()
                                + "\n\nRevise que el servidor este corriendo y que ambas maquinas"
                                + "\ntengan bien configurado java.rmi.server.hostname.",
                        "Error de conexion", JOptionPane.ERROR_MESSAGE);
            }
        }, "conexion").start();

        espera.setVisible(true);
    }

    private static JDialog crearDialogoEspera(String host) {
        JDialog dialogo = new JDialog((java.awt.Frame) null, "Conectando", true);
        dialogo.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
        dialogo.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(18, 24, 18, 24));
        panel.add(new JLabel("Esperando autorizacion del servidor " + host + "..."));
        panel.add(javax.swing.Box.createVerticalStrut(12));
        JProgressBar barra = new JProgressBar();
        barra.setIndeterminate(true);
        panel.add(barra);

        dialogo.setContentPane(panel);
        dialogo.pack();
        dialogo.setLocationRelativeTo(null);
        return dialogo;
    }

    private static void terminarConMensaje(JDialog espera, String mensaje, String titulo, int tipo) {
        SwingUtilities.invokeLater(() -> {
            espera.dispose();
            JOptionPane.showMessageDialog(null, mensaje, titulo, tipo);
            System.exit(0);
        });
    }
}
