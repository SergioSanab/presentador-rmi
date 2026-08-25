package comun;

import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/**
 * Detecta la IP real de esta maquina, la que otros equipos de la red pueden
 * alcanzar.
 *
 */
public final class Red {

    /** Fragmentos de nombre que delatan un adaptador virtual. */
    private static final String[] VIRTUALES = {
            "virtual", "vmware", "vbox", "virtualbox", "hyper-v", "hyperv",
            "wsl", "docker", "tap", "tun", "bluetooth", "loopback"
    };

    private Red() {
    }

    public static String ipLocal() {
        String ip = porTablaDeRutas();
        if (ip != null) {
            return ip;
        }
        ip = porInterfaces();
        if (ip != null) {
            return ip;
        }
        return "127.0.0.1";
    }

    /**
     * Abre un socket UDP "conectado" a una direccion externa. No se envia ni un
     * byte: connect() sobre UDP solo fija el destino, y con eso el sistema
     * operativo ya elige la interfaz de salida segun su tabla de rutas. Leer
     * getLocalAddress() devuelve entonces la IP de esa interfaz.
     */
    private static String porTablaDeRutas() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 53);
            InetAddress local = socket.getLocalAddress();
            if (local instanceof Inet4Address
                    && !local.isLoopbackAddress()
                    && !local.isAnyLocalAddress()) {
                return local.getHostAddress();
            }
        } catch (Exception e) {
            // sin ruta hacia afuera: se prueba el plan B
        }
        return null;
    }

    /** Plan B: recorrer interfaces activas descartando las virtuales. */
    private static String porInterfaces() {
        List<String> candidatas = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual() || esVirtual(ni)) {
                    continue;
                }
                Enumeration<InetAddress> direcciones = ni.getInetAddresses();
                while (direcciones.hasMoreElements()) {
                    InetAddress dir = direcciones.nextElement();
                    if (dir instanceof Inet4Address
                            && !dir.isLoopbackAddress()
                            && dir.isSiteLocalAddress()) {
                        candidatas.add(dir.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
            return null;
        }
        return candidatas.isEmpty() ? null : candidatas.get(0);
    }

    private static boolean esVirtual(NetworkInterface ni) {
        String nombre = (ni.getName() + " " + ni.getDisplayName()).toLowerCase(Locale.ROOT);
        for (String pista : VIRTUALES) {
            if (nombre.contains(pista)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fija java.rmi.server.hostname si nadie lo definio con -D. Un valor puesto
     * a mano siempre manda sobre la deteccion automatica.
     */
    public static String configurarHostname() {
        String actual = System.getProperty("java.rmi.server.hostname");
        if (actual != null && !actual.isBlank()) {
            return actual;
        }
        String ip = ipLocal();
        System.setProperty("java.rmi.server.hostname", ip);
        return ip;
    }
}