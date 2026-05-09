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

    // Generador automático de IDs
    private AtomicInteger generadorId;

    // Scheduler para viajes programados
    private ScheduledExecutorService scheduler;

    public GestorUber() {

        conductoresDisponibles = new ArrayList<>();

        viajes = new HashMap<>();

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

        viaje.setEstado(EstadoViaje.EN_CURSO);

        viajes.put(id, viaje);

        System.out.println(
                "[GESTOR] Viaje iniciado: "
                        + viaje
        );

        return viaje;
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
}