package uber.server;

import uber.shared.*;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;

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
                ObjectOutputStream out =
                        new ObjectOutputStream(socketCliente.getOutputStream());

                ObjectInputStream in =
                        new ObjectInputStream(socketCliente.getInputStream())
        ) {

            MensajeUber peticion =
                    (MensajeUber) in.readObject();

            System.out.println(
                    "[HILO " + Thread.currentThread().threadId() + "] " +
                            "Petición: " +
                            peticion.getAccion() +
                            " de " +
                            peticion.getIdUsuario() +
                            " (requestId=" + peticion.getRequestId() + ")"
            );

            out.writeObject(
                    new MensajeUber(
                            TipoMensaje.ACK,
                            "SERVIDOR",
                            "RECIBIDO",
                            peticion.getRequestId()
                    )
            );
            out.flush();

            MensajeUber respuesta =
                    gestor.procesarPeticion(peticion);

            out.writeObject(respuesta);

        } catch (Exception e) {

            System.err.println(
                    "[HILO " +
                            Thread.currentThread().threadId() +
                            "] Error con cliente: " +
                            e.getMessage()
            );

        } finally {

            try {
                socketCliente.close();
            } catch (Exception e) {
                // Ignorar
            }
        }
    }
}