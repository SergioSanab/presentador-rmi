package comun;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Callback del cliente. Al implementar esto, cada cliente es tambien un
 * objeto remoto: el servidor le llama de vuelta, por eso ambas maquinas
 * necesitan tener bien configurado java.rmi.server.hostname.
 */
public interface ObservadorPresentacion extends Remote {

    /** El servidor avisa que la proyeccion cambio de estado. */
    void estadoCambiado(EstadoPresentacion estado) throws RemoteException;

    /** Latido: si falla, el servidor asume que el cliente murio y lo retira. */
    void ping() throws RemoteException;
}
