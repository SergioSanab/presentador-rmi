package servidor;

import comun.ObservadorPresentacion;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Un cliente autorizado y vivo. Solo existe del lado del servidor. */
public class Sesion {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String token;
    private final String usuario;
    private final String host;
    private final ObservadorPresentacion observador;
    private final LocalTime desde = LocalTime.now();

    public Sesion(String token, String usuario, String host, ObservadorPresentacion observador) {
        this.token = token;
        this.usuario = usuario;
        this.host = host;
        this.observador = observador;
    }

    public String getToken() {
        return token;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getHost() {
        return host;
    }

    public ObservadorPresentacion getObservador() {
        return observador;
    }

    @Override
    public String toString() {
        return usuario + "  @ " + host + "   (desde " + desde.format(HORA) + ")";
    }
}
