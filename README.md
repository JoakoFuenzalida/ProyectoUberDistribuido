# Uber Distribuido

Sistema distribuido de gestión de viajes tipo Uber, desarrollado en Java con sockets TCP. Implementa coordinación mediante el algoritmo Bully, relojes lógicos de Lamport, replicación de estado y tolerancia a fallos con reelección automática de líder.

Proyecto final para la asignatura ICI-4344 Computación Paralela y Distribuida, PUCV.

## Requisitos

- Java 11 o superior (usa `ProcessHandle` y `AtomicInteger`)
- Sistema operativo: Windows, Linux o macOS
- Puertos 5000, 5001 y 5002 disponibles en localhost

## Estructura del proyecto

```
src/uber/
├── client/
│   ├── ClienteUber.java          # Cliente interactivo por consola
│   └── GeneradorCarga.java       # Generador de carga (50 hilos, 70 seg)
├── server/
│   ├── NodoUber.java             # Punto de entrada del nodo (ServerSocket + pool de hilos)
│   ├── ManejadorCliente.java     # Clasifica y rutea cada mensaje recibido
│   ├── Coordinador.java          # Algoritmo Bully, replicación y reenvío al líder
│   └── GestorUber.java           # Lógica de negocio (viajes, conductores, Lamport)
└── shared/
    ├── MensajeUber.java          # Mensaje serializable (acción, payload, reloj lógico)
    ├── TipoMensaje.java          # Enum con todos los tipos de mensaje
    ├── SolicitudViaje.java       # Datos de una solicitud (origen, destino, fecha)
    ├── Viaje.java                # Entidad viaje (id, pasajero, conductor, estado)
    ├── EstadoViaje.java          # Enum de estados (PENDIENTE, EN_CURSO, FINALIZADO...)
    ├── InfoNodo.java             # Payload para mensajes de elección Bully
    └── ReplicaEstado.java        # Payload para replicación (viaje + conductores)
```

## Compilación

Desde la carpeta `src/`:

```bash
cd src
javac -encoding UTF-8 -d out uber/shared/*.java uber/server/*.java uber/client/*.java
```

El flag `-encoding UTF-8` es necesario en Windows porque el código fuente contiene caracteres con tilde y emojis.

## Ejecución

### 1. Levantar los 3 nodos

Abrir tres terminales separadas y ejecutar un nodo en cada una:

```bash
# Terminal 1
java -cp out uber.server.NodoUber 5000 NODO_1

# Terminal 2
java -cp out uber.server.NodoUber 5001 NODO_2

# Terminal 3
java -cp out uber.server.NodoUber 5002 NODO_3
```

Cada nodo imprime su PID al arrancar. Anotar el PID de NODO_3 (puerto 5002) porque será el líder inicial y se usa para la prueba de fallo inducido.

Esperar unos 7-10 segundos para que los nodos se descubran entre sí y completen la primera elección Bully. En los logs debería aparecer un mensaje indicando que NODO_3 se autoproclama líder (tiene el puerto más alto).

### 2. Usar el cliente interactivo

En una cuarta terminal:

```bash
java -cp out uber.client.ClienteUber
```

El cliente pide un nombre de usuario y muestra un menú con cinco opciones:

1. **Solicitar viaje inmediato**: pide origen y destino, el sistema asigna un conductor disponible.
2. **Programar viaje**: pide origen, destino y fecha futura. El viaje queda en estado PROGRAMADO y se activa automáticamente cuando llega la hora.
3. **Consultar mis viajes**: lista todos los viajes del usuario (se responde localmente desde cualquier nodo).
4. **Finalizar un viaje**: cambia el estado del viaje EN_CURSO a FINALIZADO y libera al conductor.
5. **Salir**: cierra la sesión.

El cliente se conecta al primer puerto disponible y rota automáticamente si un nodo no responde. No necesita saber cuál es el líder.

## Prueba de uso paso a paso

Esta prueba verifica el flujo completo: solicitud de viaje, reenvío transparente, replicación y consulta local.

### Paso 1: Solicitar un viaje

En el cliente interactivo, elegir opción 1:

```
Ingrese su nombre de usuario: joaquin

Seleccione una opción: 1
Ingrese origen: Mall Plaza
Ingrese destino: Universidad
```

Observar en los logs de los nodos:
- Si el cliente se conectó a NODO_1 (seguidor), NODO_1 reenvía la petición a NODO_3 (líder).
- NODO_3 procesa la solicitud, asigna un conductor y replica el viaje hacia NODO_1 y NODO_2.
- El cliente recibe la confirmación con el nombre del conductor asignado.

### Paso 2: Consultar viajes desde otro nodo

Sin cerrar el cliente, elegir opción 3 (Consultar mis viajes). La consulta se puede responder desde cualquier nodo porque es una lectura local. En los logs del nodo que recibe la consulta no debería haber reenvío al líder.

### Paso 3: Finalizar el viaje

Elegir opción 4 (Finalizar viaje). El sistema cambia el estado a FINALIZADO y libera al conductor. Esta operación es una escritura, así que si el cliente está conectado a un seguidor, se reenvía al líder.

### Paso 4: Verificar replicación

Abrir un segundo cliente en otra terminal, conectarse con otro usuario y consultar viajes. Los viajes del primer usuario no aparecen (cada usuario ve solo los suyos), pero el hecho de que la consulta funcione en cualquier nodo confirma que la replicación está operativa.

## Prueba de carga

El generador de carga lanza 50 clientes concurrentes que ejecutan ciclos de solicitar, consultar y finalizar viajes durante 70 segundos. Imprime métricas cada 5 segundos y un reporte final.

```bash
java -cp out uber.client.GeneradorCarga
```

Parámetros opcionales:

```bash
java -cp out uber.client.GeneradorCarga 100 90    # 100 clientes, 90 segundos
```

### Métricas que imprime

En cada ventana de 5 segundos:
- `env`: peticiones enviadas
- `ok`: respuestas exitosas
- `err_neg`: errores de negocio (sin conductores, viaje ya activo, etc.)
- `err_red`: errores de red (nodo caído, timeout)
- `req/s`: throughput
- `avg` y `p95`: latencia promedio y percentil 95

Al finalizar, imprime un reporte con totales, latencia min/avg/p50/p95/p99/max y la cantidad de mensajes de coordinación generados.

## Prueba de fallo inducido

Esta prueba demuestra la tolerancia a fallos: se mata al líder en plena carga y se observa cómo el sistema se recupera solo.

### Paso 1: Levantar los 3 nodos

Seguir las instrucciones de la sección "Levantar los 3 nodos". Anotar el PID de NODO_3.

### Paso 2: Ejecutar el generador de carga

```bash
java -cp out uber.client.GeneradorCarga
```

### Paso 3: Matar al líder a los ~30 segundos

Mientras el generador está corriendo, ir a la terminal de NODO_3 y presionar `Ctrl+C`. Alternativamente, usar el PID anotado:

```bash
# Windows (PowerShell)
Stop-Process -Id <PID_NODO_3> -Force

# Linux/macOS
kill <PID_NODO_3>
```

Si `Ctrl+C` no mata el proceso Java correctamente en Windows, buscar el PID real con:

```powershell
netstat -ano | Select-String ":5002.*LISTEN"
```

Y matar ese PID con `Stop-Process`.

### Paso 4: Observar la recuperación

En la salida del generador de carga:
- Las ventanas antes del kill muestran `err_red=0`.
- La ventana donde ocurre el kill muestra un pico de `err_red` (aprox. 33%, el tercio de peticiones que iban al nodo muerto).
- Los nodos sobrevivientes eligen un nuevo líder automáticamente (NODO_2 asume el liderazgo).
- Las ventanas posteriores muestran el servicio funcionando con 2 nodos.

En los logs de NODO_1 y NODO_2 debería aparecer:
- Detección de que NODO_3 no responde al heartbeat.
- Mensaje de que el líder cayó y se dispara nueva elección.
- NODO_2 se autoproclama líder y envía COORDINATOR.

## Resultados esperados de la prueba de carga

### Sistema sano (3 nodos)

| Métrica | Valor |
|---|---|
| Throughput | ~392 req/s |
| Latencia promedio | ~1.4 ms |
| Latencia p95 | 2 ms |
| Error de red | 0% |
| Mensajes de coordinación | 2 |

### Con fallo inducido (líder muerto)

| Métrica | Valor |
|---|---|
| Throughput | ~394 req/s |
| Latencia promedio | ~1 ms |
| Latencia p95 | 2 ms |
| Error de red | ~33% |
| Mensajes de coordinación | 3 |

Los errores de negocio (~31% en ambos escenarios) son comportamiento esperado: 50 clientes compiten por 3 conductores simulados.

## Documentación adicional

- `coordinacion_lider_bully.md`: Detalle del algoritmo Bully, replicación y reenvío transparente.
- `modelo_fallos.md`: Clasificación de fallos (crash, omisión, temporización) con matriz de detección y recuperación.
- `modelo_seguridad.md`: Canales inseguros, amenazas y mitigaciones propuestas en Java.
- `prueba_carga.md`: Resultados detallados de las pruebas de carga con análisis comparativo.
- `diagramas_uml_final.md`: 9 diagramas UML en formato Mermaid (clases, secuencias, componentes, estados).

## Autores

- Martín Ignacio Basulto Nazir
- Maximiliano Andrés Bustamante Fossa
- Francisco Fernando Díaz Miranda
- Joaquín Exequiel Fuenzalida Velasquez
- Kamila Belén Leiva Morales
- Lucas Antonio Pinto Aliste
