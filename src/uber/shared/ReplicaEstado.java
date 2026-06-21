package uber.shared;

import java.io.Serializable;
import java.util.List;

public class ReplicaEstado implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Viaje viaje;
    private final List<String> conductoresDisponibles;

    public ReplicaEstado(Viaje viaje, List<String> conductoresDisponibles) {
        this.viaje = viaje;
        this.conductoresDisponibles = conductoresDisponibles;
    }

    public Viaje getViaje() {
        return viaje;
    }

    public List<String> getConductoresDisponibles() {
        return conductoresDisponibles;
    }
}
