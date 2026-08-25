package comun;

import java.io.Serializable;

/**
 * Fotografia inmutable del estado de la proyeccion. Es lo unico que viaja del
 * servidor hacia los clientes: numeros y texto, nunca imagenes.
 *
 * El campo 'version' se incrementa UNICAMENTE cuando el estado cambia de
 * verdad. Es la base del compare-and-set que vuelve idempotentes a
 * siguiente() y anterior(): el cliente manda la version que esta viendo y el
 * servidor solo aplica el cambio si todavia coincide.
 */
public class EstadoPresentacion implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String presentacion;
    private final int indice;
    private final int total;
    private final String nombreDiapositiva;
    private final boolean enPresentacion;
    private final long version;

    public EstadoPresentacion(String presentacion, int indice, int total,
        String nombreDiapositiva, boolean enPresentacion, long version) {
        this.presentacion = presentacion;
        this.indice = indice;
        this.total = total;
        this.nombreDiapositiva = nombreDiapositiva;
        this.enPresentacion = enPresentacion;
        this.version = version;
    }

    public String getPresentacion() {
        return presentacion;
    }

    /** Indice de la diapositiva actual, base 0. Vale -1 si no hay ninguna. */
    public int getIndice() {
        return indice;
    }

    public int getTotal() {
        return total;
    }

    public String getNombreDiapositiva() {
        return nombreDiapositiva;
    }

    public boolean isEnPresentacion() {
        return enPresentacion;
    }

    public long getVersion() {
        return version;
    }

    public boolean hayDiapositivas() {
        return total > 0 && indice >= 0;
    }

    /** Referencia textual de donde va la proyeccion, para la vista del cliente. */
    public String descripcionCorta() {
        if (presentacion == null) {
            return "Sin presentacion abierta";
        }
        if (!hayDiapositivas()) {
            return presentacion + " (la carpeta no tiene imagenes)";
        }
        return presentacion + "  \u00b7  " + (indice + 1) + " / " + total
                + "  \u00b7  " + nombreDiapositiva;
    }

    @Override
    public String toString() {
        return "EstadoPresentacion{" + descripcionCorta()
                + ", proyectando=" + enPresentacion
                + ", version=" + version + '}';
    }
}
