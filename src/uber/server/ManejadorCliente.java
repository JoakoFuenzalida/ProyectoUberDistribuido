package uber.server;

import uber.shared.MensajeUber;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ManejadorCliente implements Runnable {
    private Socket socketCliente;
    private GestorUber gestor;

    public ManejadorCliente(Socket socketCliente, GestorUber gestor) {
        this.socketCliente = socketCliente;
        this.gestor = gestor;
    }

    @Override
    public void run() {
        try (
                ObjectOutputStream out = new ObjectOutputStream(socketCliente.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socketCliente.getInputStream())
        ) {
            // Marshalling: recibimos el objeto complejo por la red
            MensajeUber peticion = (MensajeUber) in.readObject();

            System.out.println("[HILO] Petición recibida: " + peticion.getAccion() + " de " + peticion.getIdUsuario());

            switch (peticion.getAccion()) {
                case "SOLICITAR_VIAJE":
                    String asignado = gestor.solicitarViaje(peticion.getIdUsuario());
                    MensajeUber respuestaViaje = new MensajeUber("RESPUESTA_VIAJE", "SERVIDOR", asignado);
                    out.writeObject(respuestaViaje);
                    break;

                case "CALIFICAR":
                    int nota = (Integer) peticion.getPayload();
                    String conductorEvaluado = peticion.getIdUsuario(); // El cliente manda el nombre del conductor aquí

                    // 1. Guardamos la nota
                    gestor.calificar(conductorEvaluado, nota);

                    // 2. ¡EL ARREGLO! Liberamos al conductor para que otro lo pueda usar
                    gestor.liberarConductor(conductorEvaluado);

                    MensajeUber respuestaCalificacion = new MensajeUber("RESPUESTA_CALIFICAR", "SERVIDOR", "Calificación exitosa");
                    out.writeObject(respuestaCalificacion);
                    break;

                default:
                    out.writeObject(new MensajeUber("ERROR", "SERVIDOR", "Acción desconocida"));
            }
        } catch (Exception e) {
            // Manejo de excepciones para prevenir la caída total del sistema ante fallos parciales
            System.err.println("[HILO] Error de red con el cliente (Crash): " + e.getMessage());
        } finally {
            try { socketCliente.close(); } catch (Exception e) { /* Ignorar error al cerrar */ }
        }
    }
}