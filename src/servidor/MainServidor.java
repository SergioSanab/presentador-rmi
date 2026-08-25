package servidor;

import comun.ControlPresentacion;
import comun.Red;
import java.nio.file.Path;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Arranque del servidor.
 *
 * Uso:  java -Djava.rmi.server.hostname=<ip> servidor.MainServidor [carpeta] [puerto]
 *
 * Por defecto: carpeta "diapositivas" y puerto 1099.
 */
public final class MainServidor {

    private MainServidor() {
    }

    public static void main(String[] args) throws Exception {

        String ip = Red.configurarHostname();
        String carpeta = (args.length > 0) ? args[0] : "diapositivas";
        int puerto = (args.length > 1) ? Integer.parseInt(args[1]) : 1099;

        // La IP se pasa con -Djava.rmi.server.hostname=... desde el script.
        // Sin ella, RMI publica una direccion que los clientes remotos no
        // pueden alcanzar.
        if (ip == null || ip.isBlank()) {
            ip = "localhost";
            System.setProperty("java.rmi.server.hostname", ip);
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignorada) {
            // se queda con el aspecto por defecto
        }

        final VentanaServidor[] vistaRef = new VentanaServidor[1];
        SwingUtilities.invokeAndWait(() -> vistaRef[0] = new VentanaServidor());
        VentanaServidor vista = vistaRef[0];

        RepositorioDiapositivas repositorio = new RepositorioDiapositivas(Path.of(carpeta));

        try {
            // El objeto remoto se exporta en un puerto fijo (puerto+1) para que
            // sea facil abrirlo en el firewall.
            ServidorPresentacionImpl servidor =
                    new ServidorPresentacionImpl(repositorio, vista, puerto + 1);
            vista.setServidor(servidor);

            Registry registro = LocateRegistry.createRegistry(puerto);
            registro.rebind(ControlPresentacion.NOMBRE_SERVICIO, servidor);

            String url = "rmi://" + ip + ":" + puerto + "/" + ControlPresentacion.NOMBRE_SERVICIO;
            SwingUtilities.invokeLater(() -> vista.mostrar(
                    "Escuchando en " + url + "   |   objeto remoto en el puerto " + (puerto + 1)));
            vista.registrar("Servidor publicado en " + url);
            vista.registrar("Carpeta de diapositivas: " + repositorio.getRaiz());
            vista.registrar("Presentaciones encontradas: " + repositorio.listar().size());
            vista.estadoCambiado(servidor.estadoInicial(), null);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "No se pudo iniciar el servidor:\n" + e,
                    "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            System.exit(1);
        }
    }
}