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
        // Pool de hilos para atender concurrentemente
        ExecutorService pool = Executors.newFixedThreadPool(10);

        try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
            System.out.println("=== SERVIDOR UBER INICIADO EN PUERTO " + PUERTO + " ===");

            while (true) {
                Socket socketCliente = serverSocket.accept();
                System.out.println("[SERVIDOR] Nueva conexión desde: " + socketCliente.getInetAddress());

                // Pasamos la conexión a un hilo independiente
                pool.execute(new ManejadorCliente(socketCliente, gestor));
            }
        } catch (IOException e) {
            System.err.println("[SERVIDOR] Error crítico al iniciar: " + e.getMessage());
        }
    }
}