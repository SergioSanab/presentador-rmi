package servidor;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import java.awt.Toolkit;
import java.awt.Window;

/**
 * Ventana que notifica quien se esta conectando y deja aceptar o rechazar.
 *
 * Se muestra siempre, en cada solicitud: no hay lista blanca ni memoria de
 * conexiones anteriores, asi que un cliente que se cierre y vuelva a abrir
 * tiene que validarse otra vez.
 */
public final class DialogoAutorizacion {

    private static final String PERMITIR = "Permitir";
    private static final String RECHAZAR = "Rechazar";

    private DialogoAutorizacion() {
    }

    /** Debe invocarse en el EDT. Bloquea hasta que el operador responda. */
    public static boolean preguntar(Window padre, String usuario, String host) {
        String mensaje = "<html><body style='width:320px'>"
                + "<h3 style='margin:0 0 8px 0'>Solicitud de conexion</h3>"
                + "El usuario <b>" + escapar(usuario) + "</b> quiere conectarse"
                + " desde <b>" + escapar(host) + "</b>.<br><br>"
                + "&iquest;Permitir que controle la presentacion?"
                + "</body></html>";

        JOptionPane panel = new JOptionPane(
                mensaje,
                JOptionPane.QUESTION_MESSAGE,
                JOptionPane.YES_NO_OPTION,
                null,
                new Object[]{PERMITIR, RECHAZAR},
                RECHAZAR);

        JDialog dialogo = panel.createDialog(padre, "Nuevo cliente");
        dialogo.setAlwaysOnTop(true);
        dialogo.setModal(true);
        Toolkit.getDefaultToolkit().beep();
        dialogo.setVisible(true);
        dialogo.dispose();

        // Cerrar con la X equivale a rechazar.
        return PERMITIR.equals(panel.getValue());
    }

    private static String escapar(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
