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
    private final List<String> conductoresDisponibles;

    // Viajes almacenados
    private final Map<Integer, Viaje> viajes;

    // Cache de respuestas para peticiones repetidas
    private final Map<String, MensajeUber> cacheRespuestas;

    // Generador automático de IDs
    private final AtomicInteger generadorId;

    // Scheduler para viajes programados
    private final ScheduledExecutorService scheduler;

    // Reloj de Lamport Local
    private final AtomicInteger relojLocal;

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

        relojLocal = new AtomicInteger(0);
    }


    // =========================================
    // SOLICITAR VIAJE INMEDIATO
    // =========================================

    // RELOJES LÓGICOS DE LAMPORT

    public synchronized int obtenerEIncrementarReloj() {
        // Regla LC1: Evento interno o envío de mensaje
        return relojLocal.incrementAndGet();
    }

    public synchronized void sincronizarReloj(int relojMensaje) {
        // Regla LC2: Recepción de mensaje
        int relojActual = relojLocal.get();
        int nuevoReloj = Math.max(relojActual, relojMensaje) + 1;
        relojLocal.set(nuevoReloj);
    }

    public synchronized Viaje solicitarViaje(String pasajero, SolicitudViaje solicitud) {
        int id = generadorId.getAndIncrement();
        // Al procesar un viaje, ocurre un evento interno
        int tiempoLogico = obtenerEIncrementarReloj();

        Viaje viaje = new Viaje(id, pasajero, solicitud.getOrigen(), solicitud.getDestino(), false, null);

        if (conductoresDisponibles.isEmpty()) {
            viaje.setEstado(EstadoViaje.PENDIENTE);
            viajes.put(id, viaje);
            System.out.println("[LC: " + tiempoLogico + "] [GESTOR] Solicitud pendiente. No hay conductores.");
            return viaje;
        }

        String conductor = conductoresDisponibles.remove(0);
        viaje.setConductor(conductor);
        viaje.setEstado(EstadoViaje.EN_CURSO);
        viajes.put(id, viaje);

        System.out.println("[LC: " + tiempoLogico + "] [GESTOR] Viaje iniciado: " + viaje);

        return viaje;
    }


    public synchronized MensajeUber procesarPeticion(
            MensajeUber peticion) {

        String requestId = peticion.getRequestId();

        if (cacheRespuestas.containsKey(requestId)) {
            return cacheRespuestas.get(requestId);
        }

        MensajeUber respuesta;
        int tiempoRespuesta = obtenerEIncrementarReloj();

        switch (peticion.getAccion()) {
            case SOLICITAR_VIAJE: {
                // 1. BLOQUEO DE SEGURIDAD
                if (tieneViajeActivo(peticion.getIdUsuario())) {
                    respuesta = new MensajeUber(
                            TipoMensaje.ERROR,
                            "SERVIDOR",
                            "Operación denegada: Ya tienes un viaje activo (en curso o programado). Finalízalo antes de pedir otro.",
                            requestId,
                            tiempoRespuesta
                    );
                    break;
                }

                // 2. FLUJO NORMAL
                SolicitudViaje solicitudInmediata = (SolicitudViaje) peticion.getPayload();
                Viaje viajeInmediato = solicitarViaje(peticion.getIdUsuario(), solicitudInmediata);
                respuesta = new MensajeUber(TipoMensaje.RESPUESTA_VIAJE, "SERVIDOR", viajeInmediato, requestId, tiempoRespuesta);
                break;
            }
            case PROGRAMAR_VIAJE: {

                if (tieneViajeActivo(peticion.getIdUsuario())) {
                    respuesta = new MensajeUber(
                            TipoMensaje.ERROR,
                            "SERVIDOR",
                            "Operación denegada: Ya tienes un viaje activo (en curso o programado). Finalízalo antes de pedir otro.",
                            requestId,
                            tiempoRespuesta
                    );
                    break;
                }
                SolicitudViaje solicitudProgramada = (SolicitudViaje) peticion.getPayload();
                Viaje viajeProgramado = programarViaje(peticion.getIdUsuario(), solicitudProgramada);
                respuesta = new MensajeUber(TipoMensaje.RESPUESTA_PROGRAMAR, "SERVIDOR", viajeProgramado, requestId, tiempoRespuesta);
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
                        requestId,
                        tiempoRespuesta
                );
                break;
            }
            case FINALIZAR_VIAJE: {

                String resultado = finalizarViajeAutomatico(peticion.getIdUsuario());

                respuesta = new MensajeUber(
                        TipoMensaje.RESPUESTA_FINALIZAR,
                        "SERVIDOR",
                        resultado,
                        requestId,
                        tiempoRespuesta
                );
                break;
            }
            default:
                respuesta = new MensajeUber(
                        TipoMensaje.ERROR,
                        "SERVIDOR",
                        "Acción desconocida",
                        requestId,
                        tiempoRespuesta
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

        viaje.setEstado(EstadoViaje.EN_CURSO);

        System.out.println(
                "[SCHEDULER] Viaje programado en curso: "
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

    public synchronized String finalizarViaje(
            int idViaje,
            String pasajero) {

        Viaje viaje = viajes.get(idViaje);

        if (viaje == null) {
            return "No existe un viaje con el ID " + idViaje + ".";
        }

        if (!viaje.getPasajero().equals(pasajero)) {
            return "No tienes permiso para finalizar ese viaje.";
        }

        if (viaje.getEstado() == EstadoViaje.FINALIZADO) {
            return "El viaje #" + idViaje + " ya fue finalizado.";
        }

        if (viaje.getEstado() == EstadoViaje.PROGRAMADO) {
            return "El viaje #" + idViaje
                    + " aún no ha comenzado (está programado).";
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

        return "Viaje #" + idViaje
                + " finalizado correctamente. "
                + "Conductor " + viaje.getConductor()
                + " liberado.";
    }
    // =========================================
    // VALIDACIÓN DE ESTADO DEL USUARIO
    // =========================================
    private synchronized boolean tieneViajeActivo(String pasajero) {
        for (Viaje viaje : viajes.values()) {
            // Si el viaje es de este pasajero y NO está finalizado, significa que está activo
            if (viaje.getPasajero().equals(pasajero) && viaje.getEstado() != EstadoViaje.FINALIZADO) {
                return true;
            }
        }
        return false;
    }

    // =========================================
    // FINALIZAR VIAJE AUTOMÁTICO (Sin pedir ID)
    // =========================================
    public synchronized String finalizarViajeAutomatico(String pasajero) {
        Viaje viajeActivo = null;

        // Buscamos cuál es el viaje que este pasajero tiene actualmente EN CURSO
        for (Viaje viaje : viajes.values()) {
            if (viaje.getPasajero().equals(pasajero) && viaje.getEstado() == EstadoViaje.EN_CURSO) {
                viajeActivo = viaje;
                break;
            }
        }

        if (viajeActivo == null) {
            return "No tienes ningún viaje EN CURSO en este momento para finalizar.";
        }

        // Finalizamos el viaje encontrado y liberamos al conductor
        viajeActivo.setEstado(EstadoViaje.FINALIZADO);
        if (viajeActivo.getConductor() != null) {
            conductoresDisponibles.add(viajeActivo.getConductor());
        }

        System.out.println("[GESTOR] Viaje finalizado automáticamente: " + viajeActivo);
        return "Viaje finalizado correctamente. Conductor " + viajeActivo.getConductor() + " ha sido liberado.";
    }
}