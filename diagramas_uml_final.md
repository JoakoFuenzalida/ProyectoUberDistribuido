# Diagramas UML — Proyecto Final Uber Distribuido

Todos los diagramas están en formato Mermaid. Para renderizarlos pueden usar:
- El preview de Mermaid en VS Code (extensión "Markdown Preview Mermaid Support")
- https://mermaid.live (pegar el código y exportar como imagen)
- El propio editor de Google Docs/Word con plugins de Mermaid

---

## 1. Diagrama de Clases (Arquitectura General)

```mermaid
classDiagram
    direction TB

    class NodoUber {
        -int puerto
        -String idNodo
        +main(String[] args)
        -escucharConexiones(ServerSocket, ExecutorService, GestorUber, Coordinador)
        -conectarConVecinos(GestorUber, Coordinador)
    }

    class GestorUber {
        -List~String~ conductoresDisponibles
        -Map~Integer,Viaje~ viajes
        -Map~String,MensajeUber~ cacheRespuestas
        -AtomicInteger generadorId
        -AtomicInteger relojLocal
        -String liderId
        -int liderPuerto
        -boolean eleccionEnCurso
        -AtomicInteger contadorMensajesCoordinacion
        +procesarPeticion(MensajeUber) MensajeUber
        +solicitarViaje(String, SolicitudViaje) Viaje
        +programarViaje(String, SolicitudViaje) Viaje
        +finalizarViajeAutomatico(String) String
        +consultarViajes(String) List~Viaje~
        +esLider() boolean
        +marcarComoLider()
        +registrarLiderExterno(String, int)
        +aplicarReplicacion(Viaje, List~String~, int)
        +obtenerSnapshotConductores() List~String~
        +obtenerEIncrementarReloj() int
        +sincronizarReloj(int)
        +setListenerReplicacion(Consumer~Viaje~)
    }

    class Coordinador {
        -String idNodo
        -int puerto
        -Map~String,Integer~ vecinos
        -GestorUber gestor
        +iniciarEleccion()
        +broadcastCoordinador()
        +replicarEstado(Viaje, List~String~)
        +reenviarALider(MensajeUber) MensajeUber
        -iniciarWatchdogEleccion()
    }

    class ManejadorCliente {
        -Socket socketCliente
        -GestorUber gestor
        -Coordinador coordinador
        +run()
        -manejarHeartbeat(MensajeUber, ObjectOutputStream)
        -manejarElection(MensajeUber, ObjectOutputStream)
        -manejarCoordinator(MensajeUber)
        -manejarReplicacion(MensajeUber)
        -reenviarOResponderError(MensajeUber) MensajeUber
    }

    class ClienteUber {
        -String IP_SERVIDOR
        -int[] PUERTOS_DISPONIBLES
        +main(String[] args)
        -enviarPeticion(MensajeUber)
    }

    class GeneradorCarga {
        -int NUM_CLIENTES
        -int DURACION_SEGUNDOS
        -AtomicInteger totalEnviados
        -AtomicInteger totalExitosos
        -AtomicInteger totalErroresNegocio
        -AtomicInteger totalErroresRed
        +main(String[] args)
        -ejecutarCliente(String, int)
        -enviarPeticion(String, TipoMensaje, Object, int)
        -imprimirReporte(int, long, int)
        -consultarMetricasServidor() int
    }

    class MensajeUber {
        -TipoMensaje accion
        -String idUsuario
        -Object payload
        -String requestId
        -int relojLogico
    }

    class Viaje {
        -int id
        -String pasajero
        -String conductor
        -String origen
        -String destino
        -EstadoViaje estado
        -boolean programado
        -LocalDateTime fechaProgramada
    }

    class SolicitudViaje {
        -String origen
        -String destino
        -boolean programado
        -LocalDateTime fechaProgramada
    }

    class InfoNodo {
        -String id
        -int puerto
    }

    class ReplicaEstado {
        -Viaje viaje
        -List~String~ conductoresDisponibles
    }

    class TipoMensaje {
        <<enumeration>>
        SOLICITAR_VIAJE
        PROGRAMAR_VIAJE
        CONSULTAR_VIAJES
        FINALIZAR_VIAJE
        RESPUESTA_VIAJE
        RESPUESTA_PROGRAMAR
        RESPUESTA_CONSULTA
        RESPUESTA_FINALIZAR
        ACK
        ERROR
        HEARTBEAT
        HEARTBEAT_ACK
        ELECTION
        ELECTION_OK
        COORDINATOR
        REPLICAR_ESTADO
        CONSULTAR_METRICAS
        RESPUESTA_METRICAS
    }

    class EstadoViaje {
        <<enumeration>>
        PROGRAMADO
        PENDIENTE
        ASIGNADO
        EN_CURSO
        FINALIZADO
        CANCELADO
    }

    NodoUber --> GestorUber : crea
    NodoUber --> Coordinador : crea
    NodoUber --> ManejadorCliente : crea por conexión
    ManejadorCliente --> GestorUber : usa
    ManejadorCliente --> Coordinador : usa
    Coordinador --> GestorUber : consulta y modifica
    ClienteUber ..> MensajeUber : envía
    GeneradorCarga ..> MensajeUber : envía
    MensajeUber --> TipoMensaje : tiene
    MensajeUber ..> SolicitudViaje : payload
    MensajeUber ..> InfoNodo : payload
    MensajeUber ..> ReplicaEstado : payload
    GestorUber --> Viaje : gestiona
    Viaje --> EstadoViaje : tiene
    ReplicaEstado --> Viaje : contiene
```

---

## 2. Secuencia: Elección de Líder (Bully) al Arrancar

```mermaid
sequenceDiagram
    autonumber
    participant N1 as NODO_1<br/>(puerto 5000)
    participant N2 as NODO_2<br/>(puerto 5001)
    participant N3 as NODO_3<br/>(puerto 5002)

    Note over N1,N3: Los 3 nodos arrancan y esperan 5s<br/>Luego cada uno inicia su propia elección

    rect rgb(240, 248, 255)
        Note over N1,N3: Fase 1: NODO_1 inicia elección
        N1->>N2: ELECTION (LC=1)
        N2-->>N1: ELECTION_OK (LC=2)
        N1->>N3: ELECTION (LC=3)
        N3-->>N1: ELECTION_OK (LC=4)
        Note right of N1: Alguien superior respondió,<br/>NODO_1 espera pasivamente
    end

    rect rgb(240, 255, 240)
        Note over N2,N3: Fase 2: NODO_2 inicia su elección
        N2->>N3: ELECTION (LC=5)
        N3-->>N2: ELECTION_OK (LC=6)
        Note right of N2: Alguien superior respondió,<br/>NODO_2 espera pasivamente
    end

    rect rgb(255, 248, 240)
        Note over N3: Fase 3: NODO_3 inicia elección
        Note right of N3: No hay nodos con puerto > 5002<br/>Nadie responde ELECTION_OK
        N3->>N3: marcarComoLider()
        N3->>N3: recalibrarGeneradorId()
        N3->>N1: COORDINATOR(id=NODO_3, puerto=5002) (LC=7)
        N3->>N2: COORDINATOR(id=NODO_3, puerto=5002) (LC=8)
    end

    Note over N1: registrarLiderExterno(NODO_3, 5002)
    Note over N2: registrarLiderExterno(NODO_3, 5002)
    Note over N1,N3: NODO_3 es el líder.<br/>El sistema está listo para recibir peticiones.
```

---

## 3. Secuencia: Solicitar Viaje con Reenvío Transparente y Replicación

```mermaid
sequenceDiagram
    autonumber
    participant C as ClienteUber
    participant N1 as NODO_1<br/>(seguidor, 5000)
    participant N3 as NODO_3<br/>(líder, 5002)
    participant N2 as NODO_2<br/>(seguidor, 5001)

    C->>N1: SOLICITAR_VIAJE(user=Ana, origen=Casa, destino=U) (LC=0)
    Note over N1: sincronizarReloj(0)
    Note over N1: Acción es escritura + no soy líder
    N1-->>C: ACK (LC=1)

    rect rgb(255, 248, 240)
        Note over N1,N3: Reenvío transparente al líder
        N1->>N3: SOLICITAR_VIAJE (mismo requestId original)
        N3-->>N1: ACK (LC=2)
        Note over N3: procesarPeticion()<br/>Asigna Conductor_Juan
        N3-->>N1: RESPUESTA_VIAJE(Viaje #1, EN_CURSO) (LC=3)
    end

    N1-->>C: RESPUESTA_VIAJE(Viaje #1, EN_CURSO) (LC=4)
    Note over C: El cliente recibió respuesta normal<br/>No sabe que lo procesó NODO_3

    rect rgb(240, 255, 240)
        Note over N3,N2: Replicación del estado (best-effort)
        N3->>N1: REPLICAR_ESTADO(Viaje #1, conductores=[Maria, Pedro]) (LC=5)
        N3->>N2: REPLICAR_ESTADO(Viaje #1, conductores=[Maria, Pedro]) (LC=6)
        Note over N1: aplicarReplicacion()
        Note over N2: aplicarReplicacion()
    end

    Note over N1,N2: Ahora los 3 nodos tienen el Viaje #1<br/>CONSULTAR_VIAJES funciona en cualquier nodo
```

---

## 4. Secuencia: Caída del Líder y Reelección Automática

```mermaid
sequenceDiagram
    autonumber
    participant N1 as NODO_1<br/>(seguidor, 5000)
    participant N2 as NODO_2<br/>(seguidor, 5001)
    participant N3 as NODO_3<br/>(líder, 5002)

    Note over N1,N3: Sistema operando normalmente.<br/>NODO_3 es el líder.

    rect rgb(255, 230, 230)
        Note over N3: NODO_3 CRASH<br/>(proceso terminado)
    end

    Note over N1: Heartbeat cada 2s

    N1->>N3: HEARTBEAT (LC=50)
    Note over N1: ConnectException<br/>Nodo inalcanzable

    Note over N1: El líder caído era NODO_3<br/>Disparar nueva elección

    rect rgb(240, 248, 255)
        Note over N1,N2: Nueva elección Bully
        N1->>N2: ELECTION (LC=51)
        N2-->>N1: ELECTION_OK (LC=52)
        Note right of N1: Superior respondió,<br/>espero COORDINATOR

        Note over N2: NODO_2 inicia su propia elección
        N2->>N3: ELECTION (LC=53)
        Note over N2: NODO_3 no responde (muerto)
        Note over N2: Nadie superior vivo
        N2->>N2: marcarComoLider()
        N2->>N2: recalibrarGeneradorId()
        N2->>N1: COORDINATOR(id=NODO_2, puerto=5001) (LC=54)
    end

    Note over N1: registrarLiderExterno(NODO_2, 5001)
    Note over N1,N2: NODO_2 es el nuevo líder.<br/>El servicio continúa sin interrupción.

    rect rgb(240, 255, 240)
        Note over N1,N2: Nuevo viaje tras reelección
        N1->>N2: SOLICITAR_VIAJE (reenvío al nuevo líder)
        Note over N2: procesarPeticion()<br/>Viaje #2 (ID recalibrado, sin colisión)
        N2-->>N1: RESPUESTA_VIAJE(Viaje #2)
    end
```

---

## 5. Secuencia: Consulta de Viajes (Lectura Local)

```mermaid
sequenceDiagram
    autonumber
    participant C as ClienteUber
    participant N2 as NODO_2<br/>(seguidor, 5001)
    participant N3 as NODO_3<br/>(líder, 5002)

    Note over N2: NODO_2 tiene copia replicada<br/>de todos los viajes

    C->>N2: CONSULTAR_VIAJES(user=Ana) (LC=0)
    Note over N2: sincronizarReloj(0)
    Note over N2: Acción es lectura<br/>Se responde localmente
    N2-->>C: ACK (LC=10)

    Note over N2: consultarViajes("Ana")<br/>Busca en copia local

    N2-->>C: RESPUESTA_CONSULTA([Viaje #1]) (LC=11)

    Note over C: Recibe el viaje que fue creado<br/>vía NODO_1 y procesado por NODO_3
    Note over N3: El líder NO participó<br/>en esta operación
```

---

## 6. Secuencia: Heartbeat con Relojes de Lamport

```mermaid
sequenceDiagram
    autonumber
    participant N1 as NODO_1<br/>(LC=10)
    participant N2 as NODO_2<br/>(LC=15)

    Note over N1: Regla LC1: evento interno<br/>LC = 10 + 1 = 11

    N1->>N2: HEARTBEAT(idNodo=NODO_1, LC=11)

    Note over N2: Regla LC2: recepción<br/>LC = max(15, 11) + 1 = 16

    Note over N2: Regla LC1: envío de respuesta<br/>LC = 16 + 1 = 17

    N2-->>N1: HEARTBEAT_ACK(LC=17)

    Note over N1: Regla LC2: recepción<br/>LC = max(11, 17) + 1 = 18

    Note over N1,N2: Ambos relojes sincronizados<br/>causalmente: N1=18, N2=17
```

---

## 7. Secuencia: Prueba de Carga con Fallo Inducido

```mermaid
sequenceDiagram
    autonumber
    participant GC as GeneradorCarga<br/>(50 hilos)
    participant N1 as NODO_1<br/>(5000)
    participant N2 as NODO_2<br/>(5001)
    participant N3 as NODO_3<br/>(5002, líder)

    rect rgb(240, 255, 240)
        Note over GC,N3: Fase 1: Sistema sano (t=0s a t=30s)<br/>~392 req/s, err_red=0
        GC->>N1: SOLICITAR_VIAJE (hilo 0)
        GC->>N2: CONSULTAR_VIAJES (hilo 1)
        GC->>N3: FINALIZAR_VIAJE (hilo 2)
        Note over GC: ... 50 hilos en paralelo ...<br/>rotando puertos cada ciclo
        N1-->>GC: respuesta (vía líder N3)
        N2-->>GC: respuesta (local)
        N3-->>GC: respuesta (directo)
    end

    rect rgb(255, 230, 230)
        Note over N3: t=30s: FALLO INDUCIDO<br/>Se mata el proceso de NODO_3
    end

    rect rgb(255, 248, 240)
        Note over GC,N2: Fase 2: Impacto inmediato<br/>err_red sube a ~660/ventana (1/3 peticiones)
        GC->>N3: SOLICITAR_VIAJE
        Note over GC: ConnectException<br/>err_red++
        GC->>N1: CONSULTAR_VIAJES
        N1-->>GC: respuesta OK
        GC->>N2: FINALIZAR_VIAJE
        Note over N2: Detecta líder caído<br/>Dispara reelección
    end

    rect rgb(240, 248, 255)
        Note over N1,N2: Reelección automática
        N2->>N2: marcarComoLider()
        N2->>N1: COORDINATOR(NODO_2)
    end

    rect rgb(240, 255, 240)
        Note over GC,N2: Fase 3: Recuperación<br/>Nodos 1 y 2 operan normalmente<br/>Peticiones a puerto 5002 siguen fallando
        GC->>N1: SOLICITAR_VIAJE
        N1->>N2: reenvío al nuevo líder
        N2-->>N1: respuesta
        N1-->>GC: respuesta OK
    end

    Note over GC: Reporte final:<br/>Throughput mantenido ~394 req/s<br/>err_red = 33.37% (solo puerto muerto)
```

---

## 8. Diagrama de Componentes (Arquitectura del Sistema)

```mermaid
flowchart TB
    subgraph Cliente["Capa Cliente"]
        CU[ClienteUber<br/>Cliente interactivo]
        GC[GeneradorCarga<br/>50 hilos concurrentes]
    end

    subgraph N1["NODO_1 (puerto 5000)"]
        SS1[ServerSocket<br/>Pool 60 hilos]
        MC1[ManejadorCliente]
        GU1[GestorUber<br/>Estado replicado]
        CO1[Coordinador<br/>Bully + Replicación]
    end

    subgraph N2["NODO_2 (puerto 5001)"]
        SS2[ServerSocket<br/>Pool 60 hilos]
        MC2[ManejadorCliente]
        GU2[GestorUber<br/>Estado replicado]
        CO2[Coordinador<br/>Bully + Replicación]
    end

    subgraph N3["NODO_3 (puerto 5002) — LÍDER"]
        SS3[ServerSocket<br/>Pool 60 hilos]
        MC3[ManejadorCliente]
        GU3[GestorUber<br/>Estado maestro]
        CO3[Coordinador<br/>Bully + Replicación]
    end

    CU -->|petición a cualquier puerto| SS1
    CU -->|failover automático| SS2
    GC -->|round-robin| SS1
    GC -->|round-robin| SS2
    GC -->|round-robin| SS3

    SS1 --> MC1 --> GU1
    MC1 -->|escritura: reenvío| CO1
    CO1 -->|reenviarALider| SS3

    SS2 --> MC2 --> GU2
    MC2 -->|escritura: reenvío| CO2
    CO2 -->|reenviarALider| SS3

    SS3 --> MC3 --> GU3

    CO3 -->|REPLICAR_ESTADO| SS1
    CO3 -->|REPLICAR_ESTADO| SS2

    N1 <-->|HEARTBEAT / ELECTION| N2
    N2 <-->|HEARTBEAT / ELECTION| N3
    N1 <-->|HEARTBEAT / ELECTION| N3
```

---

## 9. Diagrama de Estados de un Viaje

```mermaid
stateDiagram-v2
    [*] --> PENDIENTE : solicitarViaje()<br/>sin conductor disponible
    [*] --> EN_CURSO : solicitarViaje()<br/>conductor asignado
    [*] --> PROGRAMADO : programarViaje()

    PROGRAMADO --> EN_CURSO : ejecutarViajeProgramado()<br/>conductor disponible
    PROGRAMADO --> PENDIENTE : ejecutarViajeProgramado()<br/>sin conductor

    EN_CURSO --> FINALIZADO : finalizarViajeAutomatico()<br/>conductor liberado

    FINALIZADO --> [*]
```

---

## Notas para el informe

- Los valores de `LC` (Lamport Clock) en los diagramas son ilustrativos. En ejecución real los valores dependen del orden exacto de los eventos.
- El diagrama 7 (prueba de carga) es un resumen simplificado; en la realidad hay 50 hilos enviando simultáneamente.
- Para exportar los diagramas como imagen: pegar el código en https://mermaid.live y descargar como PNG o SVG.
