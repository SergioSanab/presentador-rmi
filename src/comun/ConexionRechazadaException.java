package comun;

/** El operador del servidor pulso "Rechazar" en el dialogo de autorizacion. */
public class ConexionRechazadaException extends Exception {

    private static final long serialVersionUID = 1L;

    public ConexionRechazadaException(String motivo) {
        super(motivo);
    }
}
