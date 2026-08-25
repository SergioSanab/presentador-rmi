package comun;

import java.io.Serializable;

/** Lo que recibe el cliente cuando el servidor autoriza su conexion. */
public class ResultadoConexion implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String token;
    private final EstadoPresentacion estado;

    public ResultadoConexion(String token, EstadoPresentacion estado) {
        this.token = token;
        this.estado = estado;
    }

    /** Identificador de sesion; se envia en cada operacion posterior. */
    public String getToken() {
        return token;
    }

    public EstadoPresentacion getEstado() {
        return estado;
    }
}
