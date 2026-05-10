    # Diagrama de Secuencia UML — Programar viaje

    ```mermaid
---
id: 84328332-cb10-4354-a82d-2f405888ed76
---
sequenceDiagram
        autonumber
        participant Cliente as Cliente
        participant Hilo as Hilo
        participant Servidor as Servidor
        participant Gestor as Gestor
        participant Scheduler as Scheduler

        Cliente->>Servidor: abrir Socket(127.0.0.1:5000)
        Cliente->>Servidor: enviar MensajeUber(PROGRAMAR_VIAJE, usuario, solicitud)
        Servidor->>Hilo: aceptar conexión / crear ManejadorCliente
        Hilo->>Hilo: leer MensajeUber desde ObjectInputStream
        Hilo->>Gestor: programarViaje(usuario, solicitud)
        Gestor->>Gestor: crear Viaje(id, pasajero, origen, destino, true, fechaProgramada)
        Gestor->>Gestor: setEstado(PROGRAMADO)
        Gestor->>Gestor: almacenar viaje en Map<Integer, Viaje>
        Gestor->>Gestor: calcular delay = fechaProgramada - ahora
        Gestor->>Scheduler: schedule(ejecutarViajeProgramado(id), delay)
        Gestor-->>Hilo: devolver Viaje programado
        Hilo->>Cliente: enviar MensajeUber(RESPUESTA_PROGRAMAR, SERVIDOR, viaje)
        Cliente->>Cliente: leer respuesta desde ObjectInputStream
        Cliente->>Cliente: mostrar información del viaje programado

        Note over Scheduler,Gestor: Después del delay programado
        Scheduler->>Gestor: ejecutarViajeProgramado(id)
        Gestor->>Gestor: buscar viaje por id
        alt conductor disponible
            Gestor->>Gestor: asignar conductor
            Gestor->>Gestor: setEstado(ASIGNADO)
        else sin conductor disponible
            Gestor->>Gestor: setEstado(PENDIENTE)
        end
```

    ## Descripción de la interacción

    - `ClienteUber` construye la solicitud y la envía al servidor como un objeto serializado.
    - `ServidorUber` acepta la conexión y delega el manejo a `ManejadorCliente` en el pool de hilos.
    - `ManejadorCliente` identifica la acción `PROGRAMAR_VIAJE` y llama a `GestorUber.programarViaje(...)`.
    - `GestorUber` crea el objeto `Viaje`, lo guarda en memoria y agenda la ejecución futura con `ScheduledExecutorService`.
    - El servidor responde al cliente con un `MensajeUber` que contiene el viaje programado.
    - Cuando llega la fecha programada, el `Scheduler` invoca `ejecutarViajeProgramado(int id)` para intentar asignar un conductor.
