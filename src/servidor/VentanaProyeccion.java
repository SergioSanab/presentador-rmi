package servidor;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/**
 * La proyeccion propiamente dicha: una ventana sin bordes a pantalla completa
 * que solo pinta la imagen actual. No tiene controles, porque quien manda es
 * el cliente. La unica tecla que atiende es ESC, para poder salir desde la
 * maquina del servidor si hace falta.
 */
public class VentanaProyeccion {

    private final JFrame ventana = new JFrame("Proyeccion");
    private final PanelImagen panel = new PanelImagen();
    private final GraphicsDevice pantalla =
            GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

    private boolean visible;

    public VentanaProyeccion(Runnable alPulsarEscape) {
        ventana.setUndecorated(true);
        ventana.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        ventana.setContentPane(panel);
        ventana.setBackground(Color.BLACK);

        panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("ESCAPE"), "salir");
        panel.getActionMap().put("salir", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                alPulsarEscape.run();
            }
        });
    }

    /** Muestra la ventana (si hacia falta) y pinta la diapositiva indicada. */
    public void mostrar(Path archivo, String pieDePagina) {
        panel.definir(archivo, pieDePagina);
        if (!visible) {
            visible = true;
            if (pantalla.isFullScreenSupported()) {
                pantalla.setFullScreenWindow(ventana);
            } else {
                ventana.setExtendedState(JFrame.MAXIMIZED_BOTH);
                ventana.setVisible(true);
            }
        }
        ventana.toFront();
        panel.requestFocusInWindow();
        panel.repaint();
    }

    public void ocultar() {
        if (!visible) {
            return;
        }
        visible = false;
        if (pantalla.getFullScreenWindow() == ventana) {
            pantalla.setFullScreenWindow(null);
        }
        ventana.setVisible(false);
    }

    /** Panel que escala la imagen conservando su proporcion sobre fondo negro. */
    private static class PanelImagen extends JPanel {

        private static final long serialVersionUID = 1L;

        private transient Path archivo;
        private String pie = "";
        private transient BufferedImage imagen;
        private transient Path archivoCargado;
        private String error;

        PanelImagen() {
            setBackground(Color.BLACK);
            setFocusable(true);
        }

        void definir(Path archivo, String pie) {
            this.archivo = archivo;
            this.pie = (pie == null) ? "" : pie;
        }

        private void asegurarCargada() {
            if (archivo == null) {
                imagen = null;
                archivoCargado = null;
                error = null;
                return;
            }
            if (archivo.equals(archivoCargado) && (imagen != null || error != null)) {
                return;
            }
            archivoCargado = archivo;
            try {
                imagen = ImageIO.read(archivo.toFile());
                error = (imagen == null) ? "Formato de imagen no soportado" : null;
            } catch (Exception e) {
                imagen = null;
                error = "No se pudo abrir " + archivo.getFileName() + ": " + e.getMessage();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            asegurarCargada();

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            int ancho = getWidth();
            int alto = getHeight();

            if (imagen != null) {
                double escala = Math.min(ancho / (double) imagen.getWidth(),
                        alto / (double) imagen.getHeight());
                int anchoFinal = (int) Math.round(imagen.getWidth() * escala);
                int altoFinal = (int) Math.round(imagen.getHeight() * escala);
                g2.drawImage(imagen, (ancho - anchoFinal) / 2, (alto - altoFinal) / 2,
                        anchoFinal, altoFinal, null);
            } else {
                String texto = (error != null) ? error
                        : "La presentacion no tiene imagenes. Agregue archivos a la carpeta.";
                g2.setColor(new Color(210, 210, 210));
                g2.setFont(getFont().deriveFont(Font.PLAIN, 22f));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(texto, (ancho - fm.stringWidth(texto)) / 2, alto / 2);
            }

            if (!pie.isEmpty()) {
                g2.setColor(new Color(255, 255, 255, 110));
                g2.setFont(getFont().deriveFont(Font.PLAIN, 13f));
                g2.drawString(pie, 18, alto - 16);
            }
            g2.dispose();
        }
    }
}
