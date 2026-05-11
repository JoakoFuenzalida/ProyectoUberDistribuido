package uber.client;

import uber.shared.*;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.Scanner;
import java.util.UUID;

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

        while (true) {
            System.out.println("\n--------------------------------------");
            System.out.println(" Hola, " + idUsuario + ". ¿Qué deseas hacer?");
            System.out.println(" 1. 📍 Solicitar Viaje Inmediato");
            System.out.println(" 2. ⏰ Programar Viaje");
            System.out.println(" 3. 📋 Consultar mis Viajes");
            System.out.println(" 4. ⭐ Finalizar un Viaje");
            System.out.println(" 5. 🚪 Salir");
            System.out.println("--------------------------------------");
            System.out.print("➤ Seleccione una opción: ");

            String opcion = scanner.nextLine();

            if (opcion.equals("5")) {
                System.out.println("Cerrando sesión. ¡Buen viaje!");
                break;
            }

            MensajeUber peticion;
            // Generamos un ID único para la petición (Idempotencia)
            String requestId = UUID.randomUUID().toString();

            try {
                // NOTA: Usamos { } en cada case para aislar las variables y evitar los errores de compilación
                switch (opcion) {
                    case "1": {
                        System.out.print("➤ Ingresa tu origen: ");
                        String origen = scanner.nextLine();
                        System.out.print("➤ Ingresa tu destino: ");
                        String destino = scanner.nextLine();

                        SolicitudViaje solicitud = new SolicitudViaje(origen, destino, true, null );
                        peticion = new MensajeUber(TipoMensaje.SOLICITAR_VIAJE, idUsuario, solicitud, requestId);
                        System.out.println("\n📡 Buscando conductores cercanos...");
                        break;
                    }
                    case "2": {
                        System.out.print("➤ Ingresa tu origen: ");
                        String origen = scanner.nextLine();
                        System.out.print("➤ Ingresa tu destino: ");
                        String destino = scanner.nextLine();

                        System.out.println("\n--- OPCIONES DE PROGRAMACIÓN ---");
                        System.out.println(" 1. 📅 Fecha y hora completa (YYYY-MM-DD HH:MM)");
                        System.out.println(" 2. 🕒 Solo hora para el día de hoy (HH:MM)");
                        System.out.print("➤ Selecciona una opción: ");
                        String subOpcion = scanner.nextLine();

                        LocalDateTime fecha;
                        try {
                            if (subOpcion.equals("1")) {
                                System.out.print("➤ Ingresa la fecha y hora (ej. 2026-05-11 15:30): ");
                                String fechaStr = scanner.nextLine();
                                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                                fecha = LocalDateTime.parse(fechaStr, formatter);
                            } else if (subOpcion.equals("2")) {
                                System.out.print("➤ Ingresa la hora para hoy (ej. 22:30): ");
                                String horaStr = scanner.nextLine();
                                java.time.format.DateTimeFormatter timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
                                java.time.LocalTime hora = java.time.LocalTime.parse(horaStr, timeFormatter);
                                // Combinamos la fecha actual con la hora que ingresó el usuario
                                fecha = LocalDateTime.of(java.time.LocalDate.now(), hora);
                            } else {
                                System.out.println("❌ Opción inválida. Programando viaje para 1 minuto más...");
                                fecha = LocalDateTime.now().plusMinutes(1);
                            }

                            // Validación inteligente: Si por error en la demo pones una hora que ya pasó
                            if (fecha.isBefore(LocalDateTime.now())) {
                                System.out.println("⚠️ La hora ingresada ya pasó. Programando automáticamente para 1 minuto más...");
                                fecha = LocalDateTime.now().plusMinutes(1);
                            }

                        } catch (Exception e) {
                            System.out.println("❌ Formato incorrecto. Programando viaje por defecto para 1 minuto más...");
                            fecha = LocalDateTime.now().plusMinutes(1);
                        }

                        SolicitudViaje solicitud = new SolicitudViaje(origen, destino, true, fecha);
                        peticion = new MensajeUber(TipoMensaje.PROGRAMAR_VIAJE, idUsuario, solicitud, requestId);
                        // Mostramos la fecha final formateada para que se vea limpio
                        System.out.println("\n📡 Programando tu viaje para: " + fecha.toString().replace("T", " "));
                        break;
                    }
                    case "3": {
                        peticion = new MensajeUber(TipoMensaje.CONSULTAR_VIAJES, idUsuario, null, requestId);
                        System.out.println("\n📡 Consultando la base de datos...");
                        break;
                    }
                    case "4": {
                        peticion = new MensajeUber(TipoMensaje.FINALIZAR_VIAJE, idUsuario, null, requestId);
                        System.out.println("\n📡 Procesando pago y liberando conductor...");
                        break;
                    }
                    default:
                        System.out.println("❌ Opción no válida. Intente de nuevo.");
                        continue;
                }

                enviarPeticion(peticion);

            } catch (Exception e) {
                System.out.println("❌ Error al ingresar los datos: " + e.getMessage());
            }
        }
        scanner.close();
    }

    private static void enviarPeticion(MensajeUber peticion) {
        int MAX_INTENTOS = 3;

        for (int intento = 1; intento <= MAX_INTENTOS; intento++) {
            try (
                    Socket socket = new Socket(IP_SERVIDOR, PUERTO);
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
            ) {
                socket.setSoTimeout(5000);

                // 1. Enviamos la petición al servidor
                out.writeObject(peticion);
                out.flush();

                // 2. PRIMERA LECTURA: Recibimos el ACK
                MensajeUber ack = (MensajeUber) in.readObject();
                if (ack.getAccion() == TipoMensaje.ACK) {
                    System.out.println("   [RED] ACK recibido: El servidor está procesando la solicitud...");
                }

                // Simulamos el tiempo de espera de red
                Thread.sleep(1000);

                // 3. SEGUNDA LECTURA: Recibimos la respuesta real
                MensajeUber respuesta = (MensajeUber) in.readObject();
                System.out.println("✅ [UBER]: " + respuesta.getPayload().toString());

                return; // Éxito, salimos del loop

            } catch (Exception e) {
                System.err.println("❌ [INTENTO " + intento + "/" + MAX_INTENTOS + "]: " + e.getMessage());
                if (intento == MAX_INTENTOS) {
                    System.err.println("❌ [ERROR CRÍTICO]: Falla definitiva tras " + MAX_INTENTOS + " intentos. Servidor desconectado.");
                } else {
                    System.err.println("   Reintentando...");
                }
            }
        }
    }
}