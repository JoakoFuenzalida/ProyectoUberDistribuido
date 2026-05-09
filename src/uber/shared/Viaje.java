package uber.shared;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Viaje implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;

    private String pasajero;

    private String conductor;

    private String origen;

    private String destino;

    private EstadoViaje estado;

    private boolean programado;

    private LocalDateTime fechaProgramada;

    public Viaje(
            int id,
            String pasajero,
            String origen,
            String destino,
            boolean programado,
            LocalDateTime fechaProgramada) {

        this.id = id;
        this.pasajero = pasajero;
        this.origen = origen;
        this.destino = destino;

        this.programado = programado;

        this.fechaProgramada = fechaProgramada;

        this.estado = programado
                ? EstadoViaje.PROGRAMADO
                : EstadoViaje.PENDIENTE;
    }

    public int getId() {
        return id;
    }

    public String getPasajero() {
        return pasajero;
    }

    public String getConductor() {
        return conductor;
    }

    public void setConductor(String conductor) {
        this.conductor = conductor;
    }

    public String getOrigen() {
        return origen;
    }

    public String getDestino() {
        return destino;
    }

    public EstadoViaje getEstado() {
        return estado;
    }

    public void setEstado(EstadoViaje estado) {
        this.estado = estado;
    }

    public boolean isProgramado() {
        return programado;
    }

    public LocalDateTime getFechaProgramada() {
        return fechaProgramada;
    }

    @Override
    public String toString() {

        return "Viaje #" + id +
                " | Pasajero=" + pasajero +
                " | Conductor=" + conductor +
                " | Estado=" + estado +
                " | Origen=" + origen +
                " | Destino=" + destino +
                (programado
                        ? " | Fecha Programada=" + fechaProgramada
                        : "");
    }
}