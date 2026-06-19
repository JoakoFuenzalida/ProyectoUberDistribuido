package uber.server;

import uber.shared.MensajeUber;
import uber.shared.TipoMensaje;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.HashMap;
import java.util.Map;



public class NodoUber {
    public static class RedConfig {
        public static final Map<String, Integer> NODOS_VECINOS = new HashMap<>();

        static {
            // Definición estática de la topología multinodo (Membresía)
            NODOS_VECINOS.put("NODO_1", 5000);
            NODOS_VECINOS.put("NODO_2", 5001);
            NODOS_VECINOS.put("NODO_3", 5002);
        }
    }

    private static int puerto;
    private static String idNodo;

    public static void main(String[] args) {
        // Validar que se pasen los argumentos mínimos (Puerto e ID del Nodo)
        if (args.length < 2) {
            System.err.println("Uso correcto: java NodoUber <puerto> <idNodo>");
            System.err.println("Ejemplo: java NodoUber 5000 NODO_1");
            System.exit(1);
        }

        puerto = Integer.parseInt(args[0]);
        idNodo = args[1];

        GestorUber gestor = new GestorUber();
        ExecutorService pool = Executors.newFixedThreadPool(10);

        try (ServerSocket serverSocket = new ServerSocket(puerto)) {
            System.out.println("=====================================");
            System.out.println("     NODO UBER DISTRIBUIDO INICIADO   ");
            System.out.println(" ID Nodo: " + idNodo);
            System.out.println(" Puerto Escucha: " + puerto);
            System.out.println("=====================================");

            // Hito inicial: Ejecutar un hilo que intente conectar con los vecinos
            conectarConVecinos(gestor);

            escucharConexiones(serverSocket, pool, gestor);

        } catch (IOException e) {
            System.err.println("[NODO " + idNodo + "] Error en el puerto " + puerto + ": " + e.getMessage());
        } finally {
            pool.shutdown();
            System.out.println("[NODO " + idNodo + "] Pool de hilos cerrado.");
        }
    }

    private static void escucharConexiones(ServerSocket serverSocket, ExecutorService pool, GestorUber gestor) {
        while (!serverSocket.isClosed()) {
            try {
                Socket socketCliente = serverSocket.accept();
                //System.out.println("[NODO " + idNodo + "] Nueva conexión desde: " + socketCliente.getRemoteSocketAddress());

                // El manejador sigue procesando las peticiones concurrentes
                pool.execute(new ManejadorCliente(socketCliente, gestor));
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    System.err.println("[NODO " + idNodo + "] Error aceptando conexión: " + e.getMessage());
                }
            }
        }
    }


    private static void conectarConVecinos(GestorUber gestor) {
        Thread hiloConector = new Thread(() -> {
            System.out.println("[MEMBRESÍA] Iniciando monitoreo de nodos vecinos en la red...");
            try { Thread.sleep(5000); } catch (InterruptedException ignored) {}

            while (true) {
                for (Map.Entry<String, Integer> vecino : RedConfig.NODOS_VECINOS.entrySet()) {
                    String vecinoId = vecino.getKey();
                    int vecinoPuerto = vecino.getValue();

                    if (vecinoId.equals(idNodo)) continue;

                    try (
                            Socket socketVecino = new Socket("127.0.0.1", vecinoPuerto);
                            java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(socketVecino.getOutputStream());
                            java.io.ObjectInputStream in = new java.io.ObjectInputStream(socketVecino.getInputStream())
                    ) {
                        // Enviamos el latido con nuestro reloj lógico actual (LC1)
                        int miReloj = gestor.obtenerEIncrementarReloj();
                        MensajeUber latido = new MensajeUber(TipoMensaje.HEARTBEAT, idNodo, "PING", null, miReloj);
                        out.writeObject(latido);
                        out.flush();

                        MensajeUber respuesta = (MensajeUber) in.readObject();
                        if (respuesta.getAccion() == TipoMensaje.HEARTBEAT_ACK) {
                            // Actualizamos nuestro reloj con el de la respuesta (LC2)
                            gestor.sincronizarReloj(respuesta.getRelojLogico());
                        }

                    } catch (Exception e) {
                        System.err.println("❌ [ALERTA] No hay respuesta de " + vecinoId + " en el puerto " + vecinoPuerto + ". Nodo inalcanzable.");
                    }
                }
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            }
        });
        hiloConector.start();
    }

}