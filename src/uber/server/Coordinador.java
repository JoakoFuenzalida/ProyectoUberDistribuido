package uber.server;

import uber.shared.InfoNodo;
import uber.shared.MensajeUber;
import uber.shared.ReplicaEstado;
import uber.shared.TipoMensaje;
import uber.shared.Viaje;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Map;

/**
 * Orquesta la red para el algoritmo de elección de coordinador (Bully)
 * y la replicación de estado del líder hacia sus seguidores.
 */
public class Coordinador {

    private static final String IP_SERVIDOR = "127.0.0.1";
    private static final int TIMEOUT_MS = 1500;

    private final GestorUber gestor;
    private final String idNodo;
    private final int puerto;
    private final Map<String, Integer> vecinos;

    public Coordinador(GestorUber gestor, String idNodo, int puerto, Map<String, Integer> vecinos) {
        this.gestor = gestor;
        this.idNodo = idNodo;
        this.puerto = puerto;
        this.vecinos = vecinos;
    }

    // =========================================
    // ELECCIÓN (ALGORITMO BULLY)
    // =========================================

    public void iniciarEleccion() {
        if (!gestor.iniciarEleccionSiNoHayUnaEnCurso()) {
            return; // Ya hay una elección en curso, evitamos duplicarla
        }

        System.out.println("[BULLY] " + idNodo + " inicia una elección...");
        boolean superiorRespondio = false;

        for (Map.Entry<String, Integer> vecino : vecinos.entrySet()) {
            String vecinoId = vecino.getKey();
            int vecinoPuerto = vecino.getValue();

            if (vecinoId.equals(idNodo) || vecinoPuerto <= puerto) {
                continue; // Solo se notifica a nodos de mayor puerto (mayor ID)
            }

            try (
                    Socket socket = new Socket(IP_SERVIDOR, vecinoPuerto);
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
            ) {
                socket.setSoTimeout(TIMEOUT_MS);

                out.writeObject(new MensajeUber(TipoMensaje.ELECTION, idNodo, new InfoNodo(idNodo, puerto), null, gestor.obtenerEIncrementarReloj()));
                out.flush();
                gestor.incrementarContadorCoordinacion();

                MensajeUber respuesta = (MensajeUber) in.readObject();
                gestor.sincronizarReloj(respuesta.getRelojLogico());

                if (respuesta.getAccion() == TipoMensaje.ELECTION_OK) {
                    superiorRespondio = true;
                }

            } catch (Exception e) {
                System.err.println("[BULLY] " + vecinoId + " no respondió a ELECTION (inalcanzable).");
            }
        }

        if (!superiorRespondio) {
            gestor.marcarComoLider();
            broadcastCoordinador();
        } else {
            // Esperamos pasivamente el COORDINATOR de un nodo superior;
            // el watchdog reintenta si nunca llega (p.ej. ese nodo también cayó).
            iniciarWatchdogEleccion();
        }
    }

    private void iniciarWatchdogEleccion() {
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(4000);
            } catch (InterruptedException ignored) {
                return;
            }
            if (gestor.obtenerLiderId() == null) {
                System.out.println("[BULLY] " + idNodo + " no recibió COORDINATOR a tiempo, reintentando elección...");
                gestor.finalizarEleccionEnCurso();
                iniciarEleccion();
            } else {
                gestor.finalizarEleccionEnCurso();
            }
        });
        watchdog.setDaemon(true);
        watchdog.start();
    }

    public void broadcastCoordinador() {
        for (Map.Entry<String, Integer> vecino : vecinos.entrySet()) {
            String vecinoId = vecino.getKey();
            int vecinoPuerto = vecino.getValue();

            if (vecinoId.equals(idNodo)) {
                continue;
            }

            try (
                    Socket socket = new Socket(IP_SERVIDOR, vecinoPuerto);
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())
            ) {
                socket.setSoTimeout(TIMEOUT_MS);
                out.writeObject(new MensajeUber(TipoMensaje.COORDINATOR, idNodo, new InfoNodo(idNodo, puerto), null, gestor.obtenerEIncrementarReloj()));
                out.flush();
                gestor.incrementarContadorCoordinacion();
            } catch (Exception e) {
                System.err.println("[BULLY] No se pudo anunciar liderazgo a " + vecinoId + " (inalcanzable).");
            }
        }
    }

    // =========================================
    // REPLICACIÓN DE ESTADO (LÍDER -> SEGUIDORES)
    // =========================================

    public void replicarEstado(Viaje viaje, List<String> conductoresSnapshot) {
        for (Map.Entry<String, Integer> vecino : vecinos.entrySet()) {
            String vecinoId = vecino.getKey();
            int vecinoPuerto = vecino.getValue();

            if (vecinoId.equals(idNodo)) {
                continue;
            }

            try (
                    Socket socket = new Socket(IP_SERVIDOR, vecinoPuerto);
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())
            ) {
                socket.setSoTimeout(TIMEOUT_MS);
                out.writeObject(new MensajeUber(TipoMensaje.REPLICAR_ESTADO, idNodo, new ReplicaEstado(viaje, conductoresSnapshot), null, gestor.obtenerEIncrementarReloj()));
                out.flush();
            } catch (Exception e) {
                System.err.println("[REPLICACIÓN] Fallo de omisión replicando hacia " + vecinoId + ": " + e.getMessage());
            }
        }
    }

    // =========================================
    // REENVÍO TRANSPARENTE AL LÍDER
    // =========================================

    public MensajeUber reenviarALider(MensajeUber peticionOriginal) throws IOException, ClassNotFoundException {
        int puertoLider = gestor.obtenerLiderPuerto();
        if (puertoLider <= 0) {
            throw new IOException("No hay líder conocido actualmente.");
        }

        try (
                Socket socket = new Socket(IP_SERVIDOR, puertoLider);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
        ) {
            socket.setSoTimeout(5000);

            out.writeObject(peticionOriginal);
            out.flush();

            MensajeUber ackLider = (MensajeUber) in.readObject();
            gestor.sincronizarReloj(ackLider.getRelojLogico());

            MensajeUber respuestaLider = (MensajeUber) in.readObject();
            gestor.sincronizarReloj(respuestaLider.getRelojLogico());

            return respuestaLider;
        }
    }
}
