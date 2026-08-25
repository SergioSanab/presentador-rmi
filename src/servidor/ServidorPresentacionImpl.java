package servidor;

import comun.ConexionRechazadaException;
import comun.ControlPresentacion;
import comun.EstadoPresentacion;
import comun.InfoPresentacion;
import comun.ObservadorPresentacion;
import comun.ResultadoConexion;
import comun.SesionInvalidaException;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.rmi.server.ServerNotActiveException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Nucleo del servidor: guarda el estado de la proyeccion, la lista de sesiones
 * autorizadas y aplica las reglas de idempotencia.
 *
 * Todo el estado mutable vive detras del monitor 'bloqueo', asi que las ordenes
 * concurrentes de varios clientes se serializan y nunca se pisan a medias.
 */
public class ServidorPresentacionImpl extends UnicastRemoteObject implements ControlPresentacion {

    private static final long serialVersionUID = 1L;

    private final transient RepositorioDiapositivas repositorio;
    private final transient VistaServidor vista;

    private final transient Object bloqueo = new Object();

    // ---- estado protegido por 'bloqueo' ----
    private String presentacionActual;
    private transient List<Path> diapositivas = List.of();
    private int indice = -1;
    private boolean enPresentacion;
    private long version;
    // ----------------------------------------

    private final transient Map<String, Sesion> sesiones = new ConcurrentHashMap<>();
    private final transient ExecutorService notificador =
            Executors.newFixedThreadPool(4, tarea -> hilo(tarea, "notificador"));
    private final transient ScheduledExecutorService vigilante =
            Executors.newSingleThreadScheduledExecutor(tarea -> hilo(tarea, "vigilante"));

    public ServidorPresentacionImpl(RepositorioDiapositivas repositorio,
                                    VistaServidor vista,
                                    int puertoExportacion) throws RemoteException {
        super(puertoExportacion);
        this.repositorio = repositorio;
        this.vista = vista;
        vigilante.scheduleWithFixedDelay(this::comprobarSesiones, 5, 5, TimeUnit.SECONDS);
    }

    private static Thread hilo(Runnable tarea, String nombre) {
        Thread t = new Thread(tarea, nombre);
        t.setDaemon(true);
        return t;
    }

    // ================= conexion y sesiones =================

    @Override
    public ResultadoConexion solicitarConexion(String usuario, ObservadorPresentacion observador)
            throws RemoteException, ConexionRechazadaException {

        if (usuario == null || usuario.isBlank()) {
            throw new ConexionRechazadaException("El nombre de usuario no puede estar vacio");
        }
        String host = hostDelCliente();
        vista.registrar("Solicitud de conexion de '" + usuario + "' desde " + host);

        // Bloquea este hilo de RMI hasta que el operador responda el dialogo.
        boolean autorizado = vista.autorizar(usuario, host);
        if (!autorizado) {
            vista.registrar("RECHAZADA la conexion de '" + usuario + "'");
            throw new ConexionRechazadaException(
                    "El operador del servidor rechazo la conexion de '" + usuario + "'");
        }

        String token = UUID.randomUUID().toString();
        Sesion sesion = new Sesion(token, usuario, host, observador);
        sesiones.put(token, sesion);
        vista.registrar("ACEPTADA la conexion de '" + usuario + "'");
        publicarSesiones();
        return new ResultadoConexion(token, estadoActual());
    }

    @Override
    public void desconectar(String token) {
        Sesion sesion = (token == null) ? null : sesiones.remove(token);
        if (sesion != null) {
            vista.registrar("'" + sesion.getUsuario() + "' cerro su cliente");
            publicarSesiones();
        }
    }

    private String hostDelCliente() {
        try {
            return RemoteServer.getClientHost();
        } catch (ServerNotActiveException e) {
            return "desconocido";
        }
    }

    private Sesion exigirSesion(String token) throws SesionInvalidaException {
        Sesion sesion = (token == null) ? null : sesiones.get(token);
        if (sesion == null) {
            throw new SesionInvalidaException(
                    "Su sesion ya no es valida en el servidor. Debe conectarse y validarse de nuevo.");
        }
        return sesion;
    }

    private void publicarSesiones() {
        List<Sesion> lista = new ArrayList<>(sesiones.values());
        lista.sort(Comparator.comparing(Sesion::getUsuario, String.CASE_INSENSITIVE_ORDER));
        vista.sesionesActualizadas(lista);
    }

    /**
     * Latido periodico. Si un cliente se cerro de golpe (sin llamar a
     * desconectar) su stub deja de responder y aqui se retira de la lista.
     */
    private void comprobarSesiones() {
        for (Sesion sesion : sesiones.values()) {
            try {
                sesion.getObservador().ping();
            } catch (RemoteException e) {
                retirar(sesion, "dejo de responder");
            }
        }
    }

    private void retirar(Sesion sesion, String motivo) {
        if (sesiones.remove(sesion.getToken()) != null) {
            vista.registrar("Se retiro a '" + sesion.getUsuario() + "': " + motivo);
            publicarSesiones();
        }
    }

    // ================= consultas =================

    @Override
    public List<InfoPresentacion> listarPresentaciones(String token) throws SesionInvalidaException {
        exigirSesion(token);
        return repositorio.listar();
    }

    @Override
    public EstadoPresentacion obtenerEstado(String token) throws SesionInvalidaException {
        exigirSesion(token);
        return estadoActual();
    }

    private EstadoPresentacion estadoActual() {
        synchronized (bloqueo) {
            return construirEstado();
        }
    }

    /** Debe llamarse con 'bloqueo' tomado. */
    private EstadoPresentacion construirEstado() {
        if (presentacionActual == null || diapositivas.isEmpty() || indice < 0) {
            return new EstadoPresentacion(presentacionActual, -1, diapositivas.size(),
                    null, enPresentacion, version);
        }
        return new EstadoPresentacion(presentacionActual, indice, diapositivas.size(),
                diapositivas.get(indice).getFileName().toString(), enPresentacion, version);
    }

    /** Debe llamarse con 'bloqueo' tomado. */
    private Path archivoActual() {
        if (indice < 0 || indice >= diapositivas.size()) {
            return null;
        }
        return diapositivas.get(indice);
    }

    // ================= operaciones =================

    @Override
    public EstadoPresentacion abrirPresentacion(String token, String nombre)
            throws SesionInvalidaException {

        Sesion sesion = exigirSesion(token);
        EstadoPresentacion resultado;
        boolean cambio = false;
        String motivo = null;
        synchronized (bloqueo) {
            if (Objects.equals(nombre, presentacionActual)) {
                // Idempotente: ya esta abierta. No se reinicia al inicio a proposito;
                // para volver a la primera diapositiva esta irA(0).
                motivo = "'" + nombre + "' ya estaba abierta";
            } else {
                diapositivas = repositorio.diapositivasDe(nombre);
                presentacionActual = nombre;
                indice = diapositivas.isEmpty() ? -1 : 0;
                version++;
                cambio = true;
            }
            resultado = construirEstado();
        }
        finalizar(sesion, "abrir '" + nombre + "'", cambio, motivo);
        return resultado;
    }

    @Override
    public EstadoPresentacion presentar(String token)
            throws SesionInvalidaException {

        Sesion sesion = exigirSesion(token);
        EstadoPresentacion resultado;
        boolean cambio = false;
        String motivo = null;
        synchronized (bloqueo) {
            if (diapositivas.isEmpty()) {
                motivo = "no hay una presentacion abierta con imagenes";
            } else if (enPresentacion) {
                motivo = "ya se esta proyectando";
            } else {
                enPresentacion = true;
                version++;
                cambio = true;
            }
            resultado = construirEstado();
        }
        finalizar(sesion, "presentar", cambio, motivo);
        return resultado;
    }

    @Override
    public EstadoPresentacion terminarPresentacion(String token)
            throws SesionInvalidaException {

        Sesion sesion = exigirSesion(token);
        EstadoPresentacion resultado;
        boolean cambio = false;
        String motivo = null;
        synchronized (bloqueo) {
            if (!enPresentacion) {
                motivo = "la proyeccion ya estaba detenida";
            } else {
                enPresentacion = false;
                version++;
                cambio = true;
            }
            resultado = construirEstado();
        }
        finalizar(sesion, "terminar proyeccion", cambio, motivo);
        return resultado;
    }

    @Override
    public EstadoPresentacion siguiente(String token, long versionEsperada)
            throws SesionInvalidaException {
        return mover(token, versionEsperada, +1, "siguiente");
    }

    @Override
    public EstadoPresentacion anterior(String token, long versionEsperada)
            throws SesionInvalidaException {
        return mover(token, versionEsperada, -1, "anterior");
    }

    /**
     * Comandos relativos, con compare-and-set sobre la version.
     *
     * Si dos clientes pulsan "siguiente" a la vez, ambos mandan la misma
     * version: el primero avanza y sube la version, el segundo encuentra que su
     * version quedo vieja, no hace nada y recibe el estado vigente. Resultado:
     * una sola diapositiva de avance, que es lo que espera el que presenta.
     */
    private EstadoPresentacion mover(String token, long versionEsperada,
                                     int paso, String etiqueta) throws SesionInvalidaException {

        Sesion sesion = exigirSesion(token);
        EstadoPresentacion resultado;
        boolean cambio = false;
        String motivo = null;
        synchronized (bloqueo) {
            int destino = indice + paso;
            if (versionEsperada != version) {
                motivo = "version desactualizada (el cliente traia " + versionEsperada
                        + " y el estado va en " + version + ")";
            } else if (diapositivas.isEmpty()) {
                motivo = "no hay una presentacion abierta con imagenes";
            } else if (destino < 0) {
                motivo = "ya esta en la primera diapositiva";
            } else if (destino >= diapositivas.size()) {
                motivo = "ya esta en la ultima diapositiva";
            } else {
                indice = destino;
                version++;
                cambio = true;
            }
            resultado = construirEstado();
        }
        finalizar(sesion, etiqueta, cambio, motivo);
        return resultado;
    }

    @Override
    public EstadoPresentacion irA(String token, int destino)
            throws SesionInvalidaException {

        Sesion sesion = exigirSesion(token);
        EstadoPresentacion resultado;
        boolean cambio = false;
        String motivo = null;
        synchronized (bloqueo) {
            // Salto absoluto: no necesita compare-and-set, repetirlo deja el
            // mismo estado.
            if (diapositivas.isEmpty()) {
                motivo = "no hay una presentacion abierta con imagenes";
            } else if (destino < 0 || destino >= diapositivas.size()) {
                motivo = "la diapositiva " + (destino + 1) + " no existe";
            } else if (destino == indice) {
                motivo = "ya estaba en esa diapositiva";
            } else {
                indice = destino;
                version++;
                cambio = true;
            }
            resultado = construirEstado();
        }
        finalizar(sesion, "ir a " + (destino + 1), cambio, motivo);
        return resultado;
    }

    // ================= propagacion =================

    private void finalizar(Sesion sesion, String etiqueta, boolean cambio, String motivo) {
        if (cambio) {
            vista.registrar("'" + sesion.getUsuario() + "' -> " + etiqueta);
            propagar();
        } else {
            vista.registrar("'" + sesion.getUsuario() + "' -> " + etiqueta
                    + " SIN EFECTO: " + motivo);
        }
    }

    /** Actualiza la proyeccion local y avisa a todos los clientes. */
    private void propagar() {
        EstadoPresentacion estado;
        Path archivo;
        synchronized (bloqueo) {
            estado = construirEstado();
            archivo = archivoActual();
        }
        vista.estadoCambiado(estado, archivo);
        for (Sesion sesion : sesiones.values()) {
            // Una tarea por cliente: uno lento no bloquea a los demas ni al
            // hilo de RMI que atendio la orden.
            notificador.submit(() -> {
                try {
                    sesion.getObservador().estadoCambiado(estado);
                } catch (RemoteException e) {
                    retirar(sesion, "no se le pudo notificar");
                }
            });
        }
    }

    /** Detener la proyeccion desde el propio servidor (tecla ESC). */
    public void terminarDesdeServidor() {
        boolean cambio = false;
        synchronized (bloqueo) {
            if (enPresentacion) {
                enPresentacion = false;
                version++;
                cambio = true;
            }
        }
        if (cambio) {
            vista.registrar("Proyeccion detenida desde el servidor");
            propagar();
        }
    }

    /** Estado inicial para pintar el panel al arrancar. */
    public EstadoPresentacion estadoInicial() {
        return estadoActual();
    }
}