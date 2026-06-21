package uber.server;

import uber.shared.*;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.EnumSet;
import java.util.Set;


public class ManejadorCliente implements Runnable {

    // Acciones que mutan el estado compartido: solo las procesa el líder
    private static final Set<TipoMensaje> ACCIONES_ESCRITURA = EnumSet.of(
            TipoMensaje.SOLICITAR_VIAJE,
            TipoMensaje.PROGRAMAR_VIAJE,
            TipoMensaje.FINALIZAR_VIAJE
    );

    private final Socket socketCliente;
    private final GestorUber gestor;
    private final Coordinador coordinador;

    public ManejadorCliente(Socket socketCliente, GestorUber gestor, Coordinador coordinador) {
        this.socketCliente = socketCliente;
        this.gestor = gestor;
        this.coordinador = coordinador;
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

            switch (peticion.getAccion()) {
                case HEARTBEAT:
                    manejarHeartbeat(peticion, out);
                    return;
                case ELECTION:
                    manejarElection(peticion, out);
                    return;
                case COORDINATOR:
                    manejarCoordinator(peticion);
                    return;
                case REPLICAR_ESTADO:
                    manejarReplicacion(peticion);
                    return;
                default:
                    break;
            }

            System.out.println(
                    "[LC: " + gestor.obtenerEIncrementarReloj() + "] [HILO " + Thread.currentThread().getId() + "] " +
                            "Petición: " + peticion.getAccion() + " de " + peticion.getIdUsuario()
            );

            // ACK para el cliente
            out.writeObject(new MensajeUber(TipoMensaje.ACK, "SERVIDOR", "RECIBIDO", peticion.getRequestId(), gestor.obtenerEIncrementarReloj()));
            out.flush();

            MensajeUber respuesta;
            if (ACCIONES_ESCRITURA.contains(peticion.getAccion()) && !gestor.esLider()) {
                // No somos el líder: reenviamos la petición original de forma transparente
                respuesta = reenviarOResponderError(peticion);
            } else {
                // Lecturas se responden localmente en cualquier nodo; escrituras solo si soy líder
                respuesta = gestor.procesarPeticion(peticion);
            }

            out.writeObject(respuesta);

        } catch (Exception e) {
            System.err.println("[HILO " + Thread.currentThread().getId() + "] Error con cliente: " + e.getMessage());
        } finally {
            try { socketCliente.close(); } catch (Exception ignored) {}
        }
    }

    private void manejarHeartbeat(MensajeUber peticion, ObjectOutputStream out) throws Exception {
        int miReloj = gestor.obtenerEIncrementarReloj();
        out.writeObject(new MensajeUber(TipoMensaje.HEARTBEAT_ACK, "SERVIDOR", "PONG", peticion.getRequestId(), miReloj));
        out.flush();
    }

    private void manejarElection(MensajeUber peticion, ObjectOutputStream out) throws Exception {
        // Por construcción, ELECTION solo llega desde un nodo de menor puerto:
        // respondemos OK y lanzamos nuestra propia elección hacia los nodos superiores a nosotros.
        int miReloj = gestor.obtenerEIncrementarReloj();
        out.writeObject(new MensajeUber(TipoMensaje.ELECTION_OK, "SERVIDOR", null, peticion.getRequestId(), miReloj));
        out.flush();
        gestor.incrementarContadorCoordinacion();

        new Thread(coordinador::iniciarEleccion).start();
    }

    private void manejarCoordinator(MensajeUber peticion) {
        InfoNodo info = (InfoNodo) peticion.getPayload();
        gestor.registrarLiderExterno(info.getId(), info.getPuerto());
    }

    private void manejarReplicacion(MensajeUber peticion) {
        ReplicaEstado replica = (ReplicaEstado) peticion.getPayload();
        gestor.aplicarReplicacion(replica.getViaje(), replica.getConductoresDisponibles(), peticion.getRelojLogico());
        System.out.println("[LC: " + gestor.obtenerEIncrementarReloj() + "] [REPLICACIÓN] Estado actualizado desde " + peticion.getIdUsuario() + ": " + replica.getViaje());
    }

    private MensajeUber reenviarOResponderError(MensajeUber peticion) {
        try {
            return coordinador.reenviarALider(peticion);
        } catch (Exception e) {
            System.err.println("[REENVÍO] Coordinador no disponible: " + e.getMessage());
            new Thread(coordinador::iniciarEleccion).start();
            return new MensajeUber(
                    TipoMensaje.ERROR,
                    "SERVIDOR",
                    "Coordinador no disponible en este momento, por favor reintenta en unos segundos.",
                    peticion.getRequestId(),
                    gestor.obtenerEIncrementarReloj()
            );
        }
    }
}
