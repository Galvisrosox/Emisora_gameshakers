# Emisora GameShakers (UDP)

Radio para la red local hecha en Java (Swing). Un equipo es la **emisora** (el locutor) y los demás son **oyentes**.
La emisora transmite su micrófono y canciones MP3; todos los oyentes conectados escuchan lo mismo al mismo tiempo.

## Cómo cumple cada requisito

| Requisito | Cómo se resolvió |
|---|---|
| Interfaz estética y fácil de usar | Swing con tema oscuro (FlatLaf), cabecera con indicador **AL AIRE / EN VIVO**, ecualizador animado con el audio real, vúmetros, lista de reproducción con arrastrar y soltar, lista de oyentes con avatar. |
| Compartir el micrófono del locutor | Botón **Abrir micrófono**: se captura con `TargetDataLine` (44,1 kHz, 16 bits, mono), se mezcla con la música y se transmite. Opción para bajar la música automáticamente mientras el locutor habla. |
| Audio reproducido simultáneamente en los oyentes | Un único flujo continuo que la emisora envía a cada oyente registrado. Todos usan el mismo buffer (~420 ms) y descartan audio viejo si se atrasan, así suenan casi al mismo tiempo. |
| Audio únicamente en MP3 | La lista solo acepta archivos `.mp3` válidos (se comprueba que tengan tramas MP3 decodificables). Lo que viaja por la red también es MP3: la mezcla (canción + micrófono) se codifica a MP3 de 128 kbps y los oyentes la decodifican. |
| Servidor local, equipos en la misma red | La emisora muestra su IP. El oyente escribe esa IP o pulsa **Buscar**, que encuentra emisoras en la red con un broadcast UDP. |
| Transporte por UDP | Todo (control y audio) va por `DatagramSocket`, puerto **5000**. Cada paquete de audio lleva tramas MP3 completas (~840 bytes) para no fragmentarse; si se pierde uno, solo se pierden ~52 ms de audio. |

## Cómo funciona

```
EMISORA                                                          OYENTES
 canción.mp3 ─► decodifica (JLayer) ─┐
                                     ├─► mezcla ─► codifica MP3 ─► UDP ─► buffer ─► decodifica ─► parlantes
 micrófono ──► captura PCM ──────────┘   (cada 26 ms, 1152 muestras)        (JLayer)
```

Protocolo (puerto UDP 5000):

| Dirección | Mensaje | Significado |
|---|---|---|
| Oyente → Emisora | `HOLA\|nombre` | Unirse |
| Oyente → Emisora | `PING` | "Sigo aquí" (cada 3 s) |
| Oyente → Emisora | `CHAO` | Salir |
| Oyente → Red | `BUSCAR` (broadcast) | Buscar emisoras |
| Emisora → Oyente | `BIENVENIDO\|nombre` | Confirmación |
| Emisora → Oyente | `INFO\|título\|mic\|oyentes` | Qué suena (cada 2 s) |
| Emisora → Oyente | `QUIEN` | No te conozco (p. ej. la emisora se reinició): repite `HOLA` |
| Emisora → Oyente | `AQUI\|nombre` | Respuesta a `BUSCAR` |
| Emisora → Oyente | `ADIOS` | La emisora se apagó |
| Emisora → Oyente | `[0][secuencia 4 bytes][tramas MP3]` | Audio |

- La emisora saca de la lista a quien envía `CHAO` o lleva **10 s** sin `PING`.
- El oyente da la conexión por perdida si pasa **15 s** sin recibir nada.
- El número de secuencia permite mostrar al oyente cuántos paquetes se perdieron.

## Estructura

```
src/main/java/org/gameshakers/
├── Lanzador.java              elige entre emisora y oyente (punto de entrada del JAR)
├── comun/                     Protocolo, Datagrama, TramasMp3, BufferPcm, Red
│   └── ui/                    Tema, Cabecera, Tarjeta, Iconos, VuMetro, Ecualizador, Analizador
├── servidor/                  EmisoraSvr (ventana), ServidorUdp, Mezclador, FuenteMp3, FuenteMicrofono, Oyente
└── cliente/                   CliOyente (ventana), ClienteUdp, Reproductor
```

Librerías (Maven Central): `javazoom:jlayer:1.0.1` (decodificar MP3), `de.sciss:jump3r:1.0.5` (codificar MP3, port de LAME en Java), `com.formdev:flatlaf:3.5.4` (apariencia).

## Ejecutar

Requiere Java 17 o superior.

### En IntelliJ IDEA
1. **File → Open** y elegir la carpeta del proyecto (IntelliJ lo reconoce como proyecto Maven y descarga las librerías).
2. Ejecutar **EmisoraSvr** (ya viene la configuración en `.run/`), pulsar **Iniciar emisora** y fijarse en la IP que muestra.
3. Ejecutar **CliOyente** una o varias veces (la configuración ya permite varias instancias). Escribir el nombre, la IP de la emisora (o pulsar **Buscar**) y **Conectar**.
4. En la emisora: **Agregar MP3** (o arrastrar archivos) y reproducir; **Abrir micrófono** para hablar.

### Con el JAR
```bash
mvn package
java -jar target/emisora.jar              # pregunta: locutor u oyente
java -jar target/emisora.jar emisora      # abre directo la emisora
java -jar target/emisora.jar oyente 192.168.1.20
```

### En varios equipos
- Todos deben estar en la misma red.
- En el equipo de la emisora, el **Firewall de Windows** debe permitir Java (redes privadas). Si **Buscar** no encuentra la emisora, escribe la IP a mano: algunas redes bloquean el broadcast.
- Si cierras el oyente con el botón rojo de IntelliJ, se cierra a la fuerza y no alcanza a enviar `CHAO`; la emisora lo quita a los ~10 s. Cerrando la ventana o con **Desconectar** sale al instante.
