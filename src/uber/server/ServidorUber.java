package uber.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServidorUber {

    private static final int PUERTO = 5000;

    public static void main(String[] args) {

        GestorUber gestor = new GestorUber();

        ExecutorService pool =
                Executors.newFixedThreadPool(10);

        try (ServerSocket serverSocket =
                     new ServerSocket(PUERTO)) {

            System.out.println(
                    "====================================="
            );
            System.out.println(
                    " SERVIDOR UBER DISTRIBUIDO INICIADO "
            );
            System.out.println(
                    " Puerto: " + PUERTO
            );
            System.out.println(
                    "====================================="
            );

            escucharConexiones(serverSocket, pool, gestor);

        } catch (IOException e) {

            System.err.println(
                    "[SERVIDOR] No se pudo abrir el puerto "
                            + PUERTO + ": " + e.getMessage()
            );

        } finally {

            pool.shutdown();

            System.out.println(
                    "[SERVIDOR] Pool de hilos cerrado."
            );
        }
    }

    private static void escucharConexiones(
            ServerSocket serverSocket,
            ExecutorService pool,
            GestorUber gestor) {

        while (!serverSocket.isClosed()) {

            try {

                Socket socketCliente =
                        serverSocket.accept();

                System.out.println(
                        "[SERVIDOR] Nueva conexión desde: "
                                + socketCliente.getInetAddress()
                );

                pool.execute(
                        new ManejadorCliente(
                                socketCliente,
                                gestor
                        )
                );

            } catch (IOException e) {

                if (!serverSocket.isClosed()) {

                    System.err.println(
                            "[SERVIDOR] Error aceptando conexión: "
                                    + e.getMessage()
                                    + " — continuando..."
                    );
                }
            }
        }
    }
}