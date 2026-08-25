package servidor;

import comun.InfoPresentacion;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Acceso al disco. Cada subcarpeta de la raiz es una presentacion y cada
 * imagen dentro de ella es una diapositiva, ordenadas por nombre de archivo.
 *
 * Nada se cachea entre llamadas a proposito: asi se pueden agregar carpetas o
 * imagenes con el servidor ya corriendo y aparecen al pulsar "Actualizar" en
 * cualquier cliente.
 */
public class RepositorioDiapositivas {

    private static final Set<String> EXTENSIONES =
            Set.of(".png", ".jpg", ".jpeg", ".gif", ".bmp");

    // Orden alfabetico por nombre de archivo. Por eso las diapositivas se
    // numeran con dos digitos: 01-, 02-, ... 10-
    private static final Comparator<Path> POR_NOMBRE =
            Comparator.comparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER);

    private final Path raiz;

    public RepositorioDiapositivas(Path raiz) {
        this.raiz = raiz.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.raiz);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear la carpeta " + this.raiz, e);
        }
    }

    public Path getRaiz() {
        return raiz;
    }

    /** Relee el disco en cada llamada. */
    public List<InfoPresentacion> listar() {
        List<InfoPresentacion> resultado = new ArrayList<>();
        try (DirectoryStream<Path> carpetas = Files.newDirectoryStream(raiz, Files::isDirectory)) {
            List<Path> ordenadas = new ArrayList<>();
            carpetas.forEach(ordenadas::add);
            ordenadas.sort(POR_NOMBRE);
            for (Path carpeta : ordenadas) {
                String nombre = carpeta.getFileName().toString();
                resultado.add(new InfoPresentacion(nombre, diapositivasDe(nombre).size()));
            }
        } catch (IOException e) {
            System.err.println("No se pudo leer " + raiz + ": " + e.getMessage());
        }
        return resultado;
    }

    /** Imagenes de una presentacion, ordenadas. Lista vacia si no existe. */
    public List<Path> diapositivasDe(String nombre) {
        if (!esNombreSeguro(nombre)) {
            return List.of();
        }
        Path carpeta = raiz.resolve(nombre).normalize();
        if (!carpeta.startsWith(raiz) || !Files.isDirectory(carpeta)) {
            return List.of();
        }
        List<Path> imagenes = new ArrayList<>();
        try (DirectoryStream<Path> archivos = Files.newDirectoryStream(carpeta)) {
            for (Path archivo : archivos) {
                if (Files.isRegularFile(archivo) && esImagen(archivo)) {
                    imagenes.add(archivo);
                }
            }
        } catch (IOException e) {
            System.err.println("No se pudo leer " + carpeta + ": " + e.getMessage());
            return List.of();
        }
        imagenes.sort(POR_NOMBRE);
        return imagenes;
    }

    /** Evita que un cliente pida "../../etc" y se salga de la carpeta raiz. */
    private static boolean esNombreSeguro(String nombre) {
        return nombre != null
                && !nombre.isBlank()
                && !nombre.contains("..")
                && !nombre.contains("/")
                && !nombre.contains("\\");
    }

    private static boolean esImagen(Path archivo) {
        String nombre = archivo.getFileName().toString().toLowerCase(Locale.ROOT);
        int punto = nombre.lastIndexOf('.');
        return punto >= 0 && EXTENSIONES.contains(nombre.substring(punto));
    }
}