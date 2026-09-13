# Juego de adivinanza con hilos y sockets

Actividad evaluativa del eje 3, desarrollada en Java. El cliente genera un número secreto en un hilo y el servidor intenta adivinarlo. Tres fallos consecutivos dejan la partida perdida. El cliente envía `terminar` para consultar los totales y cerrar ambos programas.

## Compilar y ejecutar

Necesitas un **JDK 17 o posterior**, con `java` y `javac` disponibles en la terminal. No hay dependencias externas. Ejecuta los comandos desde la carpeta `juego-adivinanza`:

~~~sh
java -version
javac -version
mkdir -p build/classes
javac --release 17 -encoding UTF-8 -Xlint:all -d build/classes src/juego/*.java
~~~

En Windows puedes crear `build/classes` desde el explorador si tu terminal no reconoce `mkdir -p`.

En una primera terminal inicia el servidor:

~~~sh
java -cp build/classes juego.ServidorJuego
~~~

Cuando aparezca `Servidor listo en 127.0.0.1:5000`, abre otra terminal en la misma carpeta e inicia el cliente:

~~~sh
java -cp build/classes juego.ClienteJuego
~~~

Escribe `jugar` para una ronda o `terminar` para salir. Se admiten espacios alrededor y diferencias entre mayúsculas y minúsculas. Puedes terminar sin jugar ninguna ronda. Después de `Perdiste` solo se permite terminar; para otra partida hay que ejecutar de nuevo ambos programas. Si se acaba la entrada de consola, el cliente realiza el mismo cierre que con `terminar`.

Para cambiar de puerto, ejecuta estos comandos en sus respectivas terminales:

~~~sh
java -cp build/classes juego.ServidorJuego 5001
java -cp build/classes juego.ClienteJuego 127.0.0.1 5001
~~~

El servidor escucha en `127.0.0.1`: esta versión ejecuta ambos procesos en el mismo computador.

## Organización y responsabilidades

Los cinco archivos de producción están en `src/juego/`; las pruebas, en `test/juego/PruebasJuego.java`; y los diagramas, en `docs/`.

| Clase | Responsabilidad |
| --- | --- |
| [ClienteJuego](src/juego/ClienteJuego.java) | Lee la consola, genera el secreto y coordina los mensajes del cliente. |
| [ServidorJuego](src/juego/ServidorJuego.java) | Acepta un cliente, propone los intentos y aplica las reglas. |
| [GeneradorNumero](src/juego/GeneradorNumero.java) | Implementa `Runnable` y conserva el número producido por el hilo. |
| [EstadisticasJuego](src/juego/EstadisticasJuego.java) | Mantiene totales y racha; impide registrar rondas después de perder. |
| [CanalComunicacion](src/juego/CanalComunicacion.java) | Envía y recibe mensajes, configura la espera y cierra el socket. |

Cada proceso tiene su propia instancia de `CanalComunicacion`. Los datos viajan por TCP; no se comparten objetos en memoria. Solo el servidor modifica las estadísticas.

## Recorrido de una ronda

1. Al conectarse el cliente, el servidor anuncia `RANGO:1:5`.
2. Con `jugar`, el cliente crea un `GeneradorNumero` y un `Thread` nuevos.
3. `start()` ejecuta `run()` en ese hilo. El cliente espera con `join()` y consulta `getNumero()`.
4. El cliente guarda el secreto y envía `JUGAR`.
5. El servidor genera su apuesta y responde `INTENTO:n`, antes de conocer el secreto.
6. El cliente revela `NUMERO:n`. El servidor compara y registra el resultado una sola vez.
7. El servidor responde `ACIERTO`, `DESACIERTO` o `PERDISTE`.

`Runnable` describe la tarea y `Thread` la ejecuta. El hilo por ronda permite practicar el requisito de concurrencia, aunque un cálculo tan corto no obtiene una ventaja de rendimiento. `join()` espera a que termine el hilo antes de consultar su resultado. [Documentación de Thread](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html).

Un acierto corta la racha, pero conserva los desaciertos acumulados. Fallo, fallo, acierto, fallo deja 1 acierto, 3 desaciertos y una racha de 1: todavía no se ha perdido.

## Protocolo y cierre

Los mensajes usan `writeUTF()` y `readUTF()`, con prefijo de longitud y la codificación UTF modificada de Java. No son líneas para enviar directamente desde una consola TCP. Cada envío realiza `flush()`.

- `terminar` recibe `RESUMEN:aciertos:desaciertos` y cierra la partida.
- `ERROR:COMANDO_INVALIDO`, `ERROR:NUMERO_INVALIDO` y `ERROR:PARTIDA_PERDIDA` rechazan entradas sin modificar las estadísticas.
- Al agotarse la espera durante una ronda, el servidor intenta enviar `ERROR:TIEMPO_AGOTADO` y cierra.
- El servidor también admite `terminar` mientras espera `NUMERO:n`; no cuenta esa ronda incompleta. La consola del cliente lee comandos entre rondas, por lo que no interrumpe una lectura de red en curso.

El cliente dispone de 5 segundos para conectarse y configura 10 segundos en sus lecturas de red. El servidor configura 5 segundos al esperar el número y `0` entre comandos, para que el usuario pueda pensar. Son límites de espera de lectura; no son un plazo total de ronda ni se aplican al teclado o a las escrituras. [Documentación de Socket](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/Socket.html).

Una respuesta inválida hace que el cliente informe el problema y cierre. Ante una desconexión, el servidor muestra los totales registrados; una ronda incompleta no se contabiliza. `try-with-resources` cierra los sockets también si ocurre una excepción. Se asume que el cliente revela el número original; no se incluyen mecanismos para detectar que un cliente modificado cambie su secreto.

## Requisitos y decisiones

La página 1 de **Actividad Evaluativa Eje 3.pdf** pide generación mediante hilos en el cliente, comunicación con el servidor, conteo de resultados, mensaje `Perdiste` al tercer desacierto seguido y cierre mediante `terminar`. Estas reglas están implementadas.

El enunciado no define el rango, la interfaz ni la forma de adivinar. Se adoptaron consola, enteros del 1 al 5, un intento aleatorio por ronda y una conexión por ejecución. Después de perder se bloquean las rondas y se espera `terminar`. La derrota corresponde al servidor, que es quien adivina. No hay persistencia entre partidas.

La página 2 solicita código fuente y un informe PDF con portada, objetivo, desarrollo, conclusiones y referencias. Esta carpeta contiene el programa y material para el informe; el documento académico completo se prepara por separado.

## Ajustes respecto al modelo inicial

- El canal es una variable local de `iniciar()` y se pasa como parámetro; su duración coincide con el bloque que lo cierra.
- El constructor del cliente recibe `host` y `puerto`; el rango llega en `RANGO` para mantener la misma configuración en ambos procesos.
- `procesarRonda()` devuelve `boolean` para indicar si continúa la atención del cliente.
- `GeneradorNumero.numero` es `Integer`: permite detectar una consulta anterior a la generación.
- Un constructor de servidor con acceso de paquete recibe `IntSupplier`. Las pruebas fijan los intentos sin cambiar el protocolo; el constructor público conserva el azar.

## Validación

Se compilaron producción y pruebas con JDK 26.0.1, `--release 17`, `-Xlint:all` y `-Werror`, sin advertencias. Pasaron las 10 pruebas Java y se ejecutaron cliente y servidor como procesos separados. **No se ejecutó sobre un runtime JDK 17**; `--release 17` valida la compilación contra ese nivel de Java. Consulta los casos y la evidencia en [validacion.md](docs/validacion.md).

Para repetir la suite, después de compilar las clases principales:

~~~sh
mkdir -p build/test-classes
javac --release 17 -encoding UTF-8 -Xlint:all -cp build/classes -d build/test-classes test/juego/*.java
java -cp build/classes:build/test-classes juego.PruebasJuego
~~~

En Windows utiliza `java -cp "build/classes;build/test-classes" juego.PruebasJuego`. La suite necesita abrir conexiones locales.

## Diagramas e informe

Copia el contenido de [diagrama-clases.mmd](docs/diagrama-clases.mmd) o [diagrama-secuencia.mmd](docs/diagrama-secuencia.mmd) en el editor de Mermaid. El primero explica la estructura; el segundo muestra al usuario y el orden de las interacciones.

Para el informe se incluye una [versión resumida del diagrama de clases](docs/diagrama-clases-informe.mmd), que conserva las responsabilidades y relaciones principales sin reducir tanto el tamaño del texto:

- Clases para el informe: [PNG](docs/diagrama-clases-informe.png) · [SVG](docs/diagrama-clases-informe.svg).
- Secuencia con el usuario: [PNG](docs/diagrama-secuencia.png) · [SVG](docs/diagrama-secuencia.svg).

[figuras-apa7.md](docs/figuras-apa7.md) contiene títulos, notas y párrafos para incorporar estas imágenes al informe. El modelo completo se conserva como consulta de las firmas y dependencias.
