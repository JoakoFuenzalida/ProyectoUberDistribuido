package uber.server;

import uber.shared.*;

import java.time.Duration;
import java.time.LocalDateTime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class GestorUber {

    // Recursos compartidos
    private List<String> conductoresDisponibles;

    // Viajes almacenados
    private Map<Integer, Viaje> viajes;

    // Cache de respuestas para peticiones repetidas
    private Map<String, MensajeUber> cacheRespuestas;

    // Generador automático de IDs
    private AtomicInteger generadorId;

    // Scheduler para viajes programados
    private ScheduledExecutorService scheduler;

    public GestorUber() {

        conductoresDisponibles = new ArrayList<>();

        viajes = new HashMap<>();
        cacheRespuestas = new HashMap<>();

        generadorId = new AtomicInteger(1);

        scheduler =
                Executors.newScheduledThreadPool(2);

        // Conductores simulados
        conductoresDisponibles.add("Conductor_Juan");
        conductoresDisponibles.add("Conductor_Maria");
        conductoresDisponibles.add("Conductor_Pedro");
    }

    // =========================================
    // SOLICITAR VIAJE INMEDIATO
    // =========================================

    public synchronized Viaje solicitarViaje(
            String pasajero,
            SolicitudViaje solicitud) {

        int id = generadorId.getAndIncrement();

        Viaje viaje = new Viaje(
                id,
                pasajero,
                solicitud.getOrigen(),
                solicitud.getDestino(),
                false,
                null
        );

        if (conductoresDisponibles.isEmpty()) {

            viaje.setEstado(EstadoViaje.PENDIENTE);

            viajes.put(id, viaje);

            System.out.println(
                    "[GESTOR] No hay conductores disponibles."
            );

            return viaje;
        }

        String conductor =
                conductoresDisponibles.remove(0);

        viaje.setConductor(conductor);

        viaje.setEstado(EstadoViaje.ASIGNADO);

        viajes.put(id, viaje);

        System.out.println(
                "[GESTOR] Viaje inmediato asignado: "
                        + viaje
        );

        return viaje;
    }
    public synchronized MensajeUber procesarPeticion(
            MensajeUber peticion) {

        String requestId = peticion.getRequestId();

        if (cacheRespuestas.containsKey(requestId)) {
            return cacheRespuestas.get(requestId);
        }

        MensajeUber respuesta;

        switch (peticion.getAccion()) {
            case SOLICITAR_VIAJE: {
                SolicitudViaje solicitudInmediata =
                        (SolicitudViaje) peticion.getPayload();

                Viaje viajeInmediato =
                        solicitarViaje(
                                peticion.getIdUsuario(),
                                solicitudInmediata
                        );

                respuesta = new MensajeUber(
                        TipoMensaje.RESPUESTA_VIAJE,
                        "SERVIDOR",
                        viajeInmediato,
                        requestId
                );
                break;
            }
            case PROGRAMAR_VIAJE: {
                SolicitudViaje solicitudProgramada =
                        (SolicitudViaje) peticion.getPayload();

                Viaje viajeProgramado =
                        programarViaje(
                                peticion.getIdUsuario(),
                                solicitudProgramada
                        );

                respuesta = new MensajeUber(
                        TipoMensaje.RESPUESTA_PROGRAMAR,
                        "SERVIDOR",
                        viajeProgramado,
                        requestId
                );
                break;
            }
            case CONSULTAR_VIAJES: {
                List<Viaje> viajesUsuario =
                        consultarViajes(
                                peticion.getIdUsuario()
                        );

                respuesta = new MensajeUber(
                        TipoMensaje.RESPUESTA_CONSULTA,
                        "SERVIDOR",
                        viajesUsuario,
                        requestId
                );
                break;
            }
            case FINALIZAR_VIAJE: {
                Integer idViaje =
                        (Integer) peticion.getPayload();

                finalizarViaje(idViaje);

                respuesta = new MensajeUber(
                        TipoMensaje.RESPUESTA_FINALIZAR,
                        "SERVIDOR",
                        "Viaje finalizado correctamente",
                        requestId
                );
                break;
            }
            default:
                respuesta = new MensajeUber(
                        TipoMensaje.ERROR,
                        "SERVIDOR",
                        "Acción desconocida",
                        requestId
                );
        }

        cacheRespuestas.put(requestId, respuesta);
        return respuesta;
    }
    // =========================================
    // PROGRAMAR VIAJE
    // =========================================

    public synchronized Viaje programarViaje(
            String pasajero,
            SolicitudViaje solicitud) {

        int id = generadorId.getAndIncrement();

        Viaje viaje = new Viaje(
                id,
                pasajero,
                solicitud.getOrigen(),
                solicitud.getDestino(),
                true,
                solicitud.getFechaProgramada()
        );

        viaje.setEstado(EstadoViaje.PROGRAMADO);

        viajes.put(id, viaje);

        long delay = Duration.between(
                LocalDateTime.now(),
                solicitud.getFechaProgramada()
        ).toSeconds();

        if (delay < 0) {
            delay = 0;
        }

        scheduler.schedule(
                () -> ejecutarViajeProgramado(id),
                delay,
                TimeUnit.SECONDS
        );

        System.out.println(
                "[SCHEDULER] Viaje programado exitosamente: "
                        + viaje
        );

        return viaje;
    }

    // =========================================
    // EJECUTAR VIAJE PROGRAMADO
    // =========================================

    private synchronized void ejecutarViajeProgramado(
            int idViaje) {

        Viaje viaje = viajes.get(idViaje);

        if (viaje == null) {
            return;
        }

        if (conductoresDisponibles.isEmpty()) {

            viaje.setEstado(EstadoViaje.PENDIENTE);

            System.out.println(
                    "[SCHEDULER] Sin conductores disponibles para viaje "
                            + idViaje
            );

            return;
        }

        String conductor =
                conductoresDisponibles.remove(0);

        viaje.setConductor(conductor);

        viaje.setEstado(EstadoViaje.ASIGNADO);

        System.out.println(
                "[SCHEDULER] Viaje programado ejecutado: "
                        + viaje
        );
    }

    // =========================================
    // CONSULTAR VIAJES
    // =========================================

    public synchronized List<Viaje> consultarViajes(
            String pasajero) {

        List<Viaje> resultado =
                new ArrayList<>();

        for (Viaje viaje : viajes.values()) {

            if (viaje.getPasajero()
                    .equals(pasajero)) {

                resultado.add(viaje);
            }
        }

        return resultado;
    }

    // =========================================
    // FINALIZAR VIAJE
    // =========================================

    public synchronized void finalizarViaje(
            int idViaje) {

        Viaje viaje = viajes.get(idViaje);

        if (viaje == null) {
            return;
        }

        viaje.setEstado(EstadoViaje.FINALIZADO);

        if (viaje.getConductor() != null) {

            conductoresDisponibles.add(
                    viaje.getConductor()
            );
        }

        System.out.println(
                "[GESTOR] Viaje finalizado: "
                        + viaje
        );
    }
}