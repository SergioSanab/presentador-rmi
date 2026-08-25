package cliente;

import comun.EstadoPresentacion;
import comun.ObservadorPresentacion;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.function.Consumer;

/**
 * Objeto remoto del lado del cliente. Al exportarlo, el cliente pasa a ser
 * tambien un servidor RMI: el servidor le llama de vuelta para avisarle de los
 * cambios sin que tenga que preguntar cada tanto.
 *
 * Los destinos se inyectan despues, porque el observador se exporta antes de
 * que exista la ventana (hay que pasarlo en solicitarConexion).
 */
public class ObservadorPresentacionImpl extends UnicastRemoteObject implements ObservadorPresentacion {

    private static final long serialVersionUID = 1L;

    private transient Consumer<EstadoPresentacion> alCambiarEstado = estado -> {
    };

    public ObservadorPresentacionImpl(int puerto) throws RemoteException {
        super(puerto);
    }

    public void setAlCambiarEstado(Consumer<EstadoPresentacion> destino) {
        this.alCambiarEstado = destino;
    }

    @Override
    public void estadoCambiado(EstadoPresentacion estado) {
        alCambiarEstado.accept(estado);
    }

    @Override
    public void ping() {
        // Solo tiene que responder sin lanzar excepcion.
    }

    public void cerrar() {
        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (Exception ignorada) {
            // el proceso va a terminar de todos modos
        }
    }
}
