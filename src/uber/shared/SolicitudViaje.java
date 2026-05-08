package uber.shared;

import java.io.Serializable;
import java.time.LocalDateTime;

public class SolicitudViaje
        implements Serializable {

    private static final long serialVersionUID = 1L;

    private String origen;
    private String destino;

    private boolean programado;

    private LocalDateTime fechaProgramada;

    public SolicitudViaje(
            String origen,
            String destino,
            boolean programado,
            LocalDateTime fechaProgramada) {

        this.origen = origen;
        this.destino = destino;
        this.programado = programado;
        this.fechaProgramada = fechaProgramada;
    }

    public String getOrigen() {
        return origen;
    }

    public String getDestino() {
        return destino;
    }

    public boolean isProgramado() {
        return programado;
    }

    public LocalDateTime getFechaProgramada() {
        return fechaProgramada;
    }
}