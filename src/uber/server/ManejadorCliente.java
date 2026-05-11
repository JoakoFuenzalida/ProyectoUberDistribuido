package uber.server;

import uber.shared.*;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;


public class ManejadorCliente implements Runnable {

    private final Socket socketCliente;
    private final GestorUber gestor;

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
            MensajeUber peticion = (MensajeUber) in.readObject();

            System.out.println(
                    "[HILO " + Thread.currentThread().getId() + "] " +
                            "Petición: " + peticion.getAccion() +
                            " de " + peticion.getIdUsuario() +
                            " (requestId=" + peticion.getRequestId() + ")"
            );

            // ACK para mitigar fallos
            out.writeObject(new MensajeUber(TipoMensaje.ACK, "SERVIDOR", "RECIBIDO", peticion.getRequestId()));
            out.flush();

            // LA MAGIA DE TU COMPAÑERO: Toda la lógica de negocio se procesa acá
            MensajeUber respuesta = gestor.procesarPeticion(peticion);

            // Enviamos la respuesta final al cliente
            out.writeObject(respuesta);

        } catch (Exception e) {
            System.err.println("[HILO " + Thread.currentThread().getId() + "] Error con cliente: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try { socketCliente.close(); } catch (Exception ignored) {}
        }
    }
}