package uber.client;

import uber.shared.MensajeUber;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClienteUber {
    private static final String IP_SERVIDOR = "127.0.0.1"; // Localhost
    private static final int PUERTO = 5000;

    public static void main(String[] args) {
        System.out.println("=== INICIANDO APP CLIENTE UBER ===");

        // Prueba 1: Solicitar un viaje
        enviarPeticion(new MensajeUber("SOLICITAR_VIAJE", "Pasajero_Joaquin", null));

        // Prueba 2: Calificar un viaje
        enviarPeticion(new MensajeUber("CALIFICAR", "Conductor_Juan", 5));
    }

    private static void enviarPeticion(MensajeUber peticion) {
        // Al usar try-with-resources garantizamos que los Sockets se cierren solos
        try (
                Socket socket = new Socket(IP_SERVIDOR, PUERTO);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
        ) {
            // Enviamos el objeto serializado
            out.writeObject(peticion);

            // Esperamos la respuesta
            MensajeUber respuesta = (MensajeUber) in.readObject();
            System.out.println("[CLIENTE] Respuesta del servidor: " + respuesta.getPayload());

        } catch (Exception e) {
            System.err.println("[CLIENTE] Error al contactar al servidor: " + e.getMessage());
        }
    }
}