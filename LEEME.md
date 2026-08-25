# Presentador de diapositivas distribuido (Java RMI)

El **servidor** proyecta las diapositivas. Los **clientes** son controles remotos:
no ven las imágenes, solo mandan las órdenes y saben en qué punto va la proyección.
Se pueden conectar tantos clientes como se quiera, y todos los que estén autorizados
pueden controlar.

---

## Qué hay que hacer primero

Meter imágenes en las carpetas de `diapositivas/`. Cada **subcarpeta es una
presentación** y cada imagen dentro es una diapositiva:

```
diapositivas/
├── clase-01-introduccion/
│   ├── 01-portada.png
│   ├── 02-agenda.png
│   └── 03-cierre.png
├── clase-02-rmi/
└── sustentacion-final/
```

Formatos aceptados: `.png`, `.jpg`, `.jpeg`, `.gif`, `.bmp`. Se ordenan por nombre
con orden natural, así que `2.png` va antes que `10.png`. Se pueden agregar carpetas
o imágenes **con el servidor ya corriendo**: basta con pulsar *Actualizar* en un cliente.

## Compilar

```bash
compilar.bat           # Windows
```

Requiere JDK 11 o superior (probado con JDK 21). No hace falta `rmic`.

## Ejecutar

**En la máquina del servidor:**

```bash
./ejecutar-servidor.sh 192.168.1.10      # la IP de esa máquina
ejecutar-servidor.bat 192.168.1.10
```

**En cada máquina cliente** (se pueden lanzar varias instancias, incluso en el mismo
equipo):

```bash
./ejecutar-cliente.sh 192.168.1.10 192.168.1.25    # IP del servidor, IP propia
ejecutar-cliente.bat 192.168.1.10 192.168.1.25
```

Si se omiten las IP, el programa detecta una automáticamente; sirve para pruebas en
una sola máquina, pero **en red conviene pasarlas a mano**.

---

## Cómo funciona la validación

Cada vez que un cliente arranca:

1. Pide el nombre de usuario y llama a `solicitarConexion(...)`.
2. Esa llamada **se queda bloqueada** mientras en el servidor aparece una ventana:
   *"El usuario X quiere conectarse desde 192.168.1.25. ¿Permitir?"*
3. Si el operador acepta, el cliente recibe un token de sesión y aparece en la lista
   de conectados. Si rechaza, el cliente muestra el aviso y se cierra.

**No hay lista blanca ni memoria de conexiones anteriores.** Cerrar un cliente y
volver a abrirlo dispara un diálogo nuevo, que es lo que se pedía.

El servidor detecta también las caídas: un latido cada 5 segundos comprueba que cada
cliente siga vivo, y el que no responde desaparece de la lista solo.

---

## Cómo se resolvió la idempotencia

El problema: con varios clientes controlando a la vez, `siguiente()` es una operación
*relativa*, y dos clics simultáneos (o un reintento tras un timeout) avanzarían dos
diapositivas cuando el usuario esperaba una.

Se resolvió en dos capas, ambas en `ServidorPresentacionImpl`:

**1. Deduplicación por id de operación.** Cada orden lleva un UUID generado por el
cliente. El servidor guarda los últimos 500 ids atendidos con su resultado
(`CacheOperaciones`, un LRU). Si el mismo id llega otra vez, devuelve el resultado
guardado y **no la vuelve a aplicar**. Esto cubre el doble clic y el reintento de red.

**2. Compare-and-set sobre la versión del estado.** El estado lleva un contador
`version` que sube **solo cuando algo cambia de verdad**. El cliente envía la versión
que está viendo; el servidor aplica el cambio únicamente si sigue vigente. Si dos
clientes mandan `siguiente` con la misma versión, el primero avanza y el segundo se
encuentra con la versión ya vieja: no hace nada y recibe el estado actual para
resincronizarse.

Las demás operaciones (`irA`, `abrirPresentacion`, `presentar`, `terminarPresentacion`)
fijan un estado **absoluto**, así que son idempotentes por definición: repetirlas no
cambia nada y ni siquiera incrementan la versión.

Todo el estado mutable vive detrás de un mismo monitor, así que las órdenes
concurrentes se serializan y nunca se aplican a medias.

### Comprobarlo

```bash
./probar-idempotencia.sh
```

Ejecuta diez comprobaciones sin interfaz gráfica: tres envíos del mismo id avanzan una
sola diapositiva, dos clientes con la misma versión avanzan una sola, dos hilos
concurrentes avanzan una sola, repetir `irA` al mismo destino no cambia nada, etc.

También se puede ver en vivo: en el cliente hay una casilla **"Enviar cada orden dos
veces con el mismo id"**. Al marcarla, cada botón manda la orden duplicada y en la
bitácora del servidor aparece el mensaje de que la segunda se ignoró.

---

## Estructura

```
src/
├── comun/       contrato compartido: interfaces remotas y objetos serializables
│   ├── ControlPresentacion.java      API del servidor
│   ├── ObservadorPresentacion.java   callback del cliente
│   ├── EstadoPresentacion.java       estado inmutable que viaja por la red
│   └── ...
├── servidor/
│   ├── ServidorPresentacionImpl.java estado, sesiones e idempotencia
│   ├── RepositorioDiapositivas.java  lectura del disco
│   ├── VentanaProyeccion.java        pantalla completa, solo pinta
│   ├── VentanaServidor.java          conectados + bitácora
│   ├── DialogoAutorizacion.java      la ventana de aceptar/rechazar
│   └── MainServidor.java
├── cliente/
│   ├── VentanaCliente.java           los mandos
│   ├── ObservadorPresentacionImpl.java
│   └── MainCliente.java
└── pruebas/
    └── PruebaIdempotencia.java
```

Por la red **solo viajan números y texto**: las imágenes nunca salen del servidor.

---

## Detalles de RMI que conviene tener presentes

- El servidor crea el registry en el **1099** y exporta el objeto remoto en el **1100**
  (puerto fijo, para poder abrirlo en el firewall).
- Como hay callbacks, **el cliente también es un servidor RMI**. Si el servidor no
  puede alcanzar al cliente, las notificaciones fallan y el cliente termina expulsado
  de la lista. Por eso `java.rmi.server.hostname` importa en **ambos** lados.
- `InetAddress.getLocalHost()` devuelve `127.0.1.1` en varias distribuciones de Linux;
  si esa dirección queda publicada, el otro extremo intenta conectarse a sí mismo.
  `comun/Red.java` recorre las interfaces buscando una IPv4 real para evitarlo, pero
  pasar la IP a mano en los scripts es más seguro.
- El callback del cliente sale por un puerto efímero. Si hay un firewall estricto en
  la máquina cliente, se le puede fijar un puerto:
  `java -cp clases cliente.MainCliente <ipServidor> 1099 <ipPropia> 1102`.

## Si algo falla

| Síntoma | Causa habitual |
|---|---|
| `ConnectException: Connection refused` al conectar | El servidor no está corriendo, o el 1099 está cerrado en el firewall |
| El cliente conecta pero nunca se entera de los cambios | El servidor no alcanza al cliente: revisar `java.rmi.server.hostname` en la máquina cliente |
| El cliente aparece y desaparece de la lista | El latido falla por lo mismo de arriba |
| La proyección se ve en negro con un mensaje | La carpeta no tiene imágenes |
| El diálogo de autorización no aparece | Está detrás de la ventana de proyección; se abre siempre en primer plano, pero conviene tener el panel del servidor visible |
