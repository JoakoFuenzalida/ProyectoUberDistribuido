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

            //Actualizamos nuestro reloj basándonos en el mensaje entrante
            gestor.sincronizarReloj(peticion.getRelojLogico());

            if (peticion.getAccion() == TipoMensaje.HEARTBEAT) {
                // Respondemos al latido adjuntando nuestro reloj 
                int miReloj = gestor.obtenerEIncrementarReloj();
                out.writeObject(new MensajeUber(TipoMensaje.HEARTBEAT_ACK, "SERVIDOR", "PONG", peticion.getRequestId(), miReloj));
                out.flush();
                return;
            }

            System.out.println(
                    "[LC: " + gestor.obtenerEIncrementarReloj() + "] [HILO " + Thread.currentThread().getId() + "] " +
                            "Petición: " + peticion.getAccion() + " de " + peticion.getIdUsuario()
            );

            // ACK para el cliente
            out.writeObject(new MensajeUber(TipoMensaje.ACK, "SERVIDOR", "RECIBIDO", peticion.getRequestId(), gestor.obtenerEIncrementarReloj()));
            out.flush();

            MensajeUber respuesta = gestor.procesarPeticion(peticion);

            out.writeObject(respuesta);

        } catch (Exception e) {
            System.err.println("[HILO " + Thread.currentThread().getId() + "] Error con cliente: " + e.getMessage());
        } finally {
            try { socketCliente.close(); } catch (Exception ignored) {}
        }
    }
}