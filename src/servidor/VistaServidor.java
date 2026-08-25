package servidor;

import comun.EstadoPresentacion;

import java.nio.file.Path;
import java.util.List;

/**
 * Frontera entre la logica del servidor y su interfaz grafica. Gracias a esta
 * interfaz ServidorPresentacionImpl no depende de Swing y se puede probar con
 * una implementacion falsa.
 */
public interface VistaServidor {

    /**
     * Abre el dialogo modal de autorizacion y BLOQUEA hasta que el operador
     * responda. Se invoca desde un hilo del pool de RMI, nunca desde el EDT.
     */
    boolean autorizar(String usuario, String host);

    /** El estado cambio: refrescar el panel y mostrar u ocultar la proyeccion. */
    void estadoCambiado(EstadoPresentacion estado, Path archivo);

    /** Cambio la lista de clientes conectados. */
    void sesionesActualizadas(List<Sesion> sesiones);

    /** Escribe una linea en la bitacora del servidor. */
    void registrar(String mensaje);
}
