package comun;

/**
 * El token no corresponde a ninguna sesion viva: el cliente nunca fue
 * autorizado, ya se desconecto o el servidor lo expulso. El cliente debe
 * volver a solicitar conexion (y por tanto volver a validarse).
 */
public class SesionInvalidaException extends Exception {

    private static final long serialVersionUID = 1L;

    public SesionInvalidaException(String motivo) {
        super(motivo);
    }
}
