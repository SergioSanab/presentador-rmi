package comun;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * API remota del servidor de proyeccion.
 *
 * Sobre la idempotencia: irA(), abrirPresentacion(), presentar() y
 * terminarPresentacion() fijan un estado ABSOLUTO, asi que repetirlas no cambia
 * nada por definicion.
 *
 * siguiente() y anterior() son relativas, asi que se vuelven condicionales: el
 * cliente envia la version del estado que esta viendo y el servidor solo aplica
 * el cambio si sigue vigente (compare-and-set). Si no coincide, no hace nada y
 * devuelve el estado actual para que el cliente se resincronice. Eso cubre los
 * dos casos reales: dos clientes pulsando a la vez, y un reintento del cliente
 * cuando no supo si la orden llego.
 *
 * Todos los metodos devuelven el estado resultante, aunque no hayan cambiado
 * nada: asi el cliente siempre queda sincronizado.
 */
public interface ControlPresentacion extends Remote {

        String NOMBRE_SERVICIO = "PresentadorRMI";

        ResultadoConexion solicitarConexion(String usuario, ObservadorPresentacion observador)
                throws RemoteException, ConexionRechazadaException;

        void desconectar(String token) throws RemoteException;

        List<InfoPresentacion> listarPresentaciones(String token)
                throws RemoteException, SesionInvalidaException;

        EstadoPresentacion obtenerEstado(String token)
                throws RemoteException, SesionInvalidaException;

        EstadoPresentacion abrirPresentacion(String token, String nombre)
                throws RemoteException, SesionInvalidaException;

        EstadoPresentacion presentar(String token)
                throws RemoteException, SesionInvalidaException;

        EstadoPresentacion terminarPresentacion(String token)
                throws RemoteException, SesionInvalidaException;

        EstadoPresentacion siguiente(String token, long versionEsperada)
                throws RemoteException, SesionInvalidaException;

        EstadoPresentacion anterior(String token, long versionEsperada)
                throws RemoteException, SesionInvalidaException;

        EstadoPresentacion irA(String token, int indice)
                throws RemoteException, SesionInvalidaException;
}