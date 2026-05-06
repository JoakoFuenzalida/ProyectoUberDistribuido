package uber.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GestorUber {
    // Recursos compartidos
    private List<String> conductoresDisponibles;
    private Map<String, List<Integer>> calificaciones;

    public GestorUber() {
        conductoresDisponibles = new ArrayList<>();
        calificaciones = new HashMap<>();

        // Simulamos algunos conductores en la base de datos
        conductoresDisponibles.add("Conductor_Juan");
        conductoresDisponibles.add("Conductor_Maria");
        conductoresDisponibles.add("Conductor_Pedro");
    }

    // El uso de 'synchronized' protege este recurso crítico
    public synchronized String solicitarViaje(String idPasajero) {
        if (conductoresDisponibles.isEmpty()) {
            return "SIN_CONDUCTORES";
        }
        // Retiramos al primer conductor de la lista para asignarlo
        String conductorAsignado = conductoresDisponibles.remove(0);
        System.out.println("[GESTOR] Viaje asignado: Pasajero " + idPasajero + " viaja con " + conductorAsignado);
        return conductorAsignado;
    }

    // Metodo para la Función 2: Calificación post-viaje
    public synchronized void calificar(String idUsuario, int nota) {
        calificaciones.putIfAbsent(idUsuario, new ArrayList<>());
        calificaciones.get(idUsuario).add(nota);

        // Calculamos el promedio
        double promedio = calificaciones.get(idUsuario).stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);

        System.out.println("[GESTOR] " + idUsuario + " recibió una nota de " + nota + ". Promedio actual: " + promedio);
    }
    // Agrega este metodo en GestorUber
    public synchronized void liberarConductor(String idConductor) {
        conductoresDisponibles.add(idConductor);
        System.out.println("[GESTOR] El conductor " + idConductor + " vuelve a estar disponible.");
    }
}