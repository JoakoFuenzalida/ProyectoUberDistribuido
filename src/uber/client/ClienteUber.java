package uber.client;

import uber.shared.MensajeUber;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;

public class ClienteUber {
    private static final String IP_SERVIDOR = "127.0.0.1";
    private static final int PUERTO = 5000;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("======================================");
        System.out.println("       🚗 BIENVENIDO A UBER 🚗       ");
        System.out.println("======================================");
        System.out.print("➤ Ingrese su nombre de usuario: ");
        String idUsuario = scanner.nextLine();

        System.out.println("\nIniciando sesión... conectado al servidor central.");

        // VARIABLES DE ESTADO (La magia para que sea realista)
        boolean enViaje = false;
        String conductorAsignado = null;

        while (true) {
            System.out.println("\n--------------------------------------");
            System.out.println(" Hola, " + idUsuario + ". ¿Qué deseas hacer?");

            // EL MENÚ CAMBIA SEGÚN EL ESTADO DEL CLIENTE
            if (!enViaje) {
                System.out.println(" 1. 📍 Solicitar un Viaje");
                System.out.println(" 2. 🚪 Salir");
            } else {
                System.out.println(" 1. ⭐ Terminar viaje y Calificar a " + conductorAsignado);
            }
            System.out.println("--------------------------------------");
            System.out.print("➤ Seleccione una opción: ");

            String opcion = scanner.nextLine();
            MensajeUber peticion = null;

            if (!enViaje) {
                // ACCIONES CUANDO ESTÁ LIBRE
                if (opcion.equals("2")) {
                    System.out.println("Cerrando sesión. ¡Hasta pronto!");
                    break;
                } else if (opcion.equals("1")) {
                    peticion = new MensajeUber("SOLICITAR_VIAJE", idUsuario, null);
                    System.out.println("\n📡 Buscando conductores cercanos...");
                    simularCarga();

                    String respuestaSrv = enviarPeticion(peticion);

                    // Si el servidor encontró conductor, cambiamos el estado
                    if (!respuestaSrv.equals("SIN_CONDUCTORES") && !respuestaSrv.startsWith("ERROR")) {
                        enViaje = true;
                        conductorAsignado = respuestaSrv;
                        System.out.println("✅ [UBER]: Tu conductor es " + conductorAsignado + ". ¡El viaje ha comenzado!");
                    } else {
                        System.out.println("❌ [UBER]: No hay conductores disponibles en este momento.");
                    }
                } else {
                    System.out.println("❌ Opción no válida.");
                }
            } else {
                // ACCIONES CUANDO ESTÁ EN VIAJE
                if (opcion.equals("1")) {
                    System.out.print("\n➤ Viaje terminado. Ingresa la nota para " + conductorAsignado + " (1 al 5): ");
                    int nota;
                    try {
                        nota = Integer.parseInt(scanner.nextLine());
                        if(nota < 1 || nota > 5) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        System.out.println("❌ Nota inválida. Debe ser un número del 1 al 5.");
                        continue;
                    }

                    peticion = new MensajeUber("CALIFICAR", conductorAsignado, nota);
                    System.out.println("\n📡 Enviando calificación...");
                    simularCarga();

                    enviarPeticion(peticion);
                    System.out.println("✅ [UBER]: ¡Gracias por tu calificación! Viaje finalizado.");

                    // Reiniciamos el estado para que pueda pedir otro viaje
                    enViaje = false;
                    conductorAsignado = null;
                } else {
                    System.out.println("❌ Debes terminar tu viaje actual para hacer otra acción.");
                }
            }
        }
        scanner.close();
    }

    private static void simularCarga() {
        try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // Modificamos el método para que retorne el Payload como String
    private static String enviarPeticion(MensajeUber peticion) {
        try (
                Socket socket = new Socket(IP_SERVIDOR, PUERTO);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
        ) {
            out.writeObject(peticion);
            MensajeUber respuesta = (MensajeUber) in.readObject();
            return respuesta.getPayload().toString();

        } catch (Exception e) {
            System.err.println("❌ [ERROR CRÍTICO]: No se pudo contactar al servidor. (" + e.getMessage() + ")");
            return "ERROR";
        }
    }
}