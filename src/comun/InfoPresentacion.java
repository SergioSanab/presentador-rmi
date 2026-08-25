package comun;

import java.io.Serializable;

/** Una carpeta de diapositivas disponible en el servidor. */
public class InfoPresentacion implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String nombre;
    private final int cantidadDiapositivas;

    public InfoPresentacion(String nombre, int cantidadDiapositivas) {
        this.nombre = nombre;
        this.cantidadDiapositivas = cantidadDiapositivas;
    }

    public String getNombre() {
        return nombre;
    }

    public int getCantidadDiapositivas() {
        return cantidadDiapositivas;
    }

    @Override
    public String toString() {
        return nombre + "  (" + cantidadDiapositivas + " diapositivas)";
    }
}
