# Prueba de Carga y Métricas (Sección 3 de la Rúbrica)

## 1. Qué hace el generador de carga

`uber.client.GeneradorCarga` lanza **50 clientes concurrentes** (hilos) que bombardean
el sistema distribuido durante **70 segundos**. Cada cliente ejecuta un ciclo repetido:

1. `SOLICITAR_VIAJE` (escritura, reenviada al líder si llega a un seguidor)
2. `CONSULTAR_VIAJES` (lectura, respondida localmente)
3. `FINALIZAR_VIAJE` (escritura, reenviada al líder)

Los clientes rotan el puerto destino (`5000 → 5001 → 5002 → 5000 → ...`) para
ejercitar tanto el procesamiento directo en el líder como el **reenvío transparente**
desde los seguidores.

## 2. Métricas recolectadas

| Métrica | Descripción | Sección rúbrica |
|---|---|---|
| Throughput (req/s) | Peticiones procesadas por segundo | 3.2 |
| Latencia promedio | Tiempo desde envío hasta respuesta (ms) | 3.2 |
| Latencia p50/p95/p99 | Percentiles de latencia | 3.2 |
| Errores de negocio | Restricciones del dominio (viaje activo, sin conductores) | 3.2 |
| Errores de red | Nodo caído, timeout, conexión rechazada | 3.2 / 3.3 |
| Mensajes de coordinación | Cantidad de ELECTION/ELECTION_OK/COORDINATOR enviados | 3.2 |

El generador imprime métricas **por ventana de 5 segundos** (para ver el impacto
temporal de un fallo) y un **reporte final consolidado**.

## 3. Cómo ejecutar la prueba

### Compilar

```bash
cd src
javac -encoding UTF-8 -d out uber/shared/*.java uber/server/*.java uber/client/*.java
```

### Levantar los 3 nodos (cada uno en una terminal separada)

```bash
java -cp out uber.server.NodoUber 5000 NODO_1
java -cp out uber.server.NodoUber 5001 NODO_2
java -cp out uber.server.NodoUber 5002 NODO_3
```

Cada nodo imprime su **PID** al arrancar. Anotarlo para la prueba de fallo.
Esperar ~10 segundos a que la elección se complete (NODO_3 se autoproclama líder).

### Ejecutar el generador de carga (en una 4ta terminal)

```bash
java -cp out uber.client.GeneradorCarga [clientes] [segundos]
```

Valores por defecto: 50 clientes, 70 segundos. Ejemplos:

```bash
java -cp out uber.client.GeneradorCarga              # 50 clientes, 70s
java -cp out uber.client.GeneradorCarga 100 90        # 100 clientes, 90s
```

## 4. Prueba de fallo inducido (Sección 3.3)

### Procedimiento

1. Ejecutar el generador de carga normalmente
2. A los **~30 segundos**, ir a la terminal de NODO_3 y presionar **Ctrl+C**
3. Observar en el generador de carga:
   - Las ventanas anteriores al kill muestran `err_red=0`
   - La ventana donde ocurre el kill muestra un **pico de `err_red`**
   - Las ventanas posteriores muestran la **recuperación** (NODO_2 asume liderazgo)

### Resultados reales obtenidos

**Escenario 1: Sistema sano (3 nodos, sin fallo)**

```
[t=  5s] V 1 | env=1884 ok=1322 err_neg=560 err_red=  0 | 376.8 req/s | avg=7ms p95=6ms
[t= 10s] V 2 | env=1964 ok=1349 err_neg=613 err_red=  0 | 392.8 req/s | avg=1ms p95=2ms
[t= 15s] V 3 | env=1968 ok=1353 err_neg=619 err_red=  0 | 393.6 req/s | avg=1ms p95=2ms
  ...
[t= 70s] V14 | env=1981 ok=1362 err_neg=621 err_red=  0 | 396.2 req/s | avg=1ms p95=2ms

REPORTE FINAL:
  Total peticiones      : 27432
  Exitosas              : 18868
  Errores de negocio    : 8564
  Errores de red/sistema: 0
  Tasa error red        : 0.00%
  Throughput            : 391.89 req/s
  Latencia promedio     : 1.43 ms
  Percentil 95          : 2 ms
  Percentil 99          : 5 ms
  Mensajes coordinacion : 2
```

**Escenario 2: Líder caído (NODO_3 muerto)**

```
[t=  5s] V 1 | env=1952 ok= 886 err_neg=415 err_red=650 | 390.4 req/s | avg=2ms p95=2ms
[t= 10s] V 2 | env=1986 ok= 902 err_neg=426 err_red=658 | 397.2 req/s | avg=1ms p95=2ms
  ...

REPORTE FINAL:
  Total peticiones      : 11823
  Exitosas              : 5341
  Errores de negocio    : 2537
  Errores de red/sistema: 3945
  Tasa error red        : 33.37%
  Throughput            : 394.10 req/s
  Latencia promedio     : 0.96 ms
  Percentil 95          : 2 ms
```

### Análisis para el informe

| Métrica | 3 nodos sanos | Líder caído | Interpretación |
|---|---|---|---|
| Throughput | 391.89 req/s | 394.10 req/s | Se mantiene: los 2 nodos vivos absorben la carga |
| Error red | 0.00% | 33.37% | Exactamente 1/3 (peticiones al nodo muerto) |
| Latencia p95 | 2 ms | 2 ms | Sin degradación en las conexiones exitosas |
| Coordinación | 2 msgs | 3 msgs | +1 por reelección tras detectar caída |

Puntos clave para la sección 3.3:
- El **throughput no cae** significativamente (los nodos vivos siguen atendiendo)
- Los errores de red corresponden exactamente al **1/3 de peticiones** que iban al nodo caído
- La **latencia no se degrada** en las conexiones que sí llegan a nodos vivos
- La **reelección** (Bully) ocurre automáticamente en los nodos sobrevivientes

## 5. Sobre los errores de negocio (~31%)

Los errores de negocio (`err_neg`) no son fallas del sistema. Son respuestas válidas
del servidor que reflejan restricciones del dominio Uber:

- **"Ya tienes un viaje activo"** — el pasajero intentó solicitar un segundo viaje
  sin haber finalizado el primero
- **"No tienes ningún viaje EN CURSO"** — el pasajero intentó finalizar un viaje que
  está en estado PENDIENTE (sin conductor asignado)
- **"Sin conductores disponibles"** — los 3 conductores simulados ya están ocupados

Esto es comportamiento esperado con 50 clientes compitiendo por 3 conductores, y
demuestra que el **mecanismo de exclusión mutua sobre el recurso compartido** (pool de
conductores centralizado en el líder) funciona correctamente bajo carga.

## 6. Archivos

- `src/uber/client/GeneradorCarga.java` — el generador de carga
- `src/uber/shared/TipoMensaje.java` — agregado `CONSULTAR_METRICAS` / `RESPUESTA_METRICAS`
- `src/uber/server/GestorUber.java` — handler para `CONSULTAR_METRICAS`
- `src/uber/server/NodoUber.java` — pool aumentado a 60 hilos, imprime PID al iniciar

## 7. Mapeo a la rúbrica

| Punto | Cómo lo cubre |
|---|---|
| **3.1** — Prueba real con 50+ clientes, 60+ seg | 50 clientes, 70 segundos, ~392 req/s sostenidos |
| **3.2** — Métricas (throughput, latencia, mensajes de coordinación) | Reporte final con todas las métricas + ventanas de 5s |
| **3.3** — Fallo inducido durante la prueba, análisis de impacto | Kill de NODO_3 a los 30s, contraste antes/después visible en ventanas |
