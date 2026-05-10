package uber.client;

import uber.shared.*;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

public class ClienteUber {

    private static final String IP_SERVIDOR = "127.0.0.1";
    private static final int PUERTO = 5000;
    private static final int MAX_REINTENTOS = 3;

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        System.out.println("=================================");
        System.out.println("         CLIENTE UBER           ");
        System.out.println("=================================");

        System.out.print("Ingrese nombre de usuario: ");

        String usuario = scanner.nextLine();

        while (true) {

            // ── SINCRONIZAR ESTADO CON EL SERVIDOR ────────────
            // Al inicio de cada iteración consultamos el servidor
            // para saber si el pasajero ya tiene un viaje activo
            // (EN_CURSO, PENDIENTE o PROGRAMADO).
            // Esto detecta tanto viajes programados que el scheduler
            // activó en segundo plano, como viajes pendientes por
            // falta de conductor.
            Viaje viajeActivo = obtenerViajeActivo(usuario);

            System.out.println("\n--------- MENÚ ---------");

            if (viajeActivo != null) {

                // Hay un viaje ocupando al pasajero
                System.out.println(
                        ">>> Viaje #" + viajeActivo.getId()
                                + " | Estado: " + viajeActivo.getEstado()
                                + " | " + viajeActivo.getOrigen()
                                + " → " + viajeActivo.getDestino()
                                + (viajeActivo.getConductor() != null
                                ? " | Conductor: " + viajeActivo.getConductor()
                                : " | Sin conductor asignado aún")
                );
                System.out.println("1. Consultar mis viajes");
                System.out.println("2. Finalizar viaje");
                System.out.println("3. Salir");

            } else {

                // Libre para pedir un nuevo viaje
                System.out.println("1. Solicitar viaje");
                System.out.println("2. Programar viaje");
                System.out.println("3. Consultar mis viajes");
                System.out.println("4. Salir");
            }

            System.out.print("Seleccione opción: ");
            String opcion = scanner.nextLine();

            // ── MENÚ CON VIAJE ACTIVO ──────────────────────────
            if (viajeActivo != null) {

                switch (opcion) {

                    case "1":

                        imprimirListaViajes(usuario);
                        break;

                    case "2":

                        MensajeUber respuestaFinalizar =
                                enviarPeticion(
                                        new MensajeUber(
                                                TipoMensaje.FINALIZAR_VIAJE,
                                                usuario,
                                                viajeActivo.getId()
                                        )
                                );

                        System.out.println("\n=== RESULTADO ===");
                        System.out.println(
                                respuestaFinalizar.getPayload()
                        );
                        break;

                    case "3":

                        System.out.println("Cerrando cliente...");
                        scanner.close();
                        System.exit(0);

                    default:
                        System.out.println("Opción inválida.");
                }

                continue;
            }

            // ── MENÚ SIN VIAJE ACTIVO ──────────────────────────
            switch (opcion) {

                case "1":

                    System.out.print("Origen: ");
                    String origen = scanner.nextLine();

                    System.out.print("Destino: ");
                    String destino = scanner.nextLine();

                    MensajeUber respuesta =
                            enviarPeticion(
                                    new MensajeUber(
                                            TipoMensaje.SOLICITAR_VIAJE,
                                            usuario,
                                            new SolicitudViaje(
                                                    origen,
                                                    destino,
                                                    false,
                                                    null
                                            )
                                    )
                            );

                    if (respuesta.getAccion() == TipoMensaje.ERROR) {
                        System.out.println(
                                "\n=== ERROR ==="
                        );
                        System.out.println(
                                respuesta.getPayload()
                        );
                        break;
                    }

                    System.out.println(
                            "\n=== RESPUESTA DEL SERVIDOR ==="
                    );

                    Viaje viajeRecibido =
                            (Viaje) respuesta.getPayload();

                    System.out.println(viajeRecibido);

                    if (viajeRecibido != null
                            && viajeRecibido.getEstado()
                            == EstadoViaje.EN_CURSO) {
                        System.out.println(
                                "\n¡Viaje iniciado! Conductor: "
                                        + viajeRecibido.getConductor()
                        );
                    } else {
                        System.out.println(
                                "\nNo hay conductores disponibles. "
                                        + "El viaje quedó en espera."
                        );
                    }

                    break;

                case "2":

                    System.out.print("Origen: ");
                    String origenProg = scanner.nextLine();

                    System.out.print("Destino: ");
                    String destinoProg = scanner.nextLine();

                    LocalDateTime fechaProg =
                            pedirFechaHora(scanner);

                    if (fechaProg == null) {
                        break;
                    }

                    MensajeUber respuestaProg =
                            enviarPeticion(
                                    new MensajeUber(
                                            TipoMensaje.PROGRAMAR_VIAJE,
                                            usuario,
                                            new SolicitudViaje(
                                                    origenProg,
                                                    destinoProg,
                                                    true,
                                                    fechaProg
                                            )
                                    )
                            );

                    if (respuestaProgramada.getAccion() == TipoMensaje.ERROR) {
                        System.out.println(
                                "\n=== ERROR ==="
                        );
                        System.out.println(
                                respuestaProgramada.getPayload()
                        );
                        break;
                    }

                    System.out.println(
                            "\n=== VIAJE PROGRAMADO ==="
                    );

                    System.out.println(
                            respuestaProgramada.getPayload()
                    );

                    System.out.println("\n=== VIAJE PROGRAMADO ===");
                    System.out.println(respuestaProg.getPayload());
                    break;

                case "3":

                    imprimirListaViajes(usuario);
                    break;

                    if (respuestaConsulta.getAccion() == TipoMensaje.ERROR) {
                        System.out.println(
                                "\n=== ERROR ==="
                        );
                        System.out.println(
                                respuestaConsulta.getPayload()
                        );
                        break;
                    }

                    @SuppressWarnings("unchecked")
                    List<Viaje> viajes =
                            (List<Viaje>) respuestaConsulta.getPayload();
                case "4":

                    System.out.println("Cerrando cliente...");
                    scanner.close();
                    System.exit(0);

                default:
                    System.out.println("Opción inválida.");
            }
        }
    }

    // Solicita al usuario la fecha y hora del viaje programado.
    // Ofrece tres modos:
    //   1 — ingresar fecha y hora completa (dd/MM/yyyy HH:mm)
    //   2 — ingresar solo la hora de hoy          (HH:mm)
    //   3 — modo demo: N segundos desde ahora
    // Retorna null si la entrada es inválida.
    private static LocalDateTime pedirFechaHora(Scanner scanner) {

        System.out.println("\n¿Cómo desea ingresar la hora del viaje?");
        System.out.println("  1. Fecha y hora exacta  (dd/MM/yyyy HH:mm)");
        System.out.println("  2. Solo la hora de hoy  (HH:mm)");
        System.out.print("Opción: ");

        String modo = scanner.nextLine().trim();

        try {

            switch (modo) {

                case "1": {

                    System.out.print(
                            "Ingrese fecha y hora (dd/MM/yyyy HH:mm): "
                    );

                    String entrada = scanner.nextLine().trim();

                    java.time.format.DateTimeFormatter fmt =
                            java.time.format.DateTimeFormatter
                                    .ofPattern("dd/MM/yyyy HH:mm");

                    LocalDateTime dt =
                            LocalDateTime.parse(entrada, fmt);

                    if (dt.isBefore(LocalDateTime.now())) {
                        System.out.println(
                                "[ERROR] La fecha ingresada ya pasó."
                        );
                        return null;
                    }

                    if (respuestaFinalizar.getAccion() == TipoMensaje.ERROR) {
                        System.out.println(
                                "\n=== ERROR ==="
                        );
                        System.out.println(
                                respuestaFinalizar.getPayload()
                        );
                        break;
                    }

                    System.out.println(
                            "[INFO] Viaje programado para: " + dt
                    );

                    return dt;
                }

                case "2": {

                    System.out.print(
                            "Ingrese la hora (HH:mm): "
                    );

                    String entrada = scanner.nextLine().trim();

                    java.time.format.DateTimeFormatter fmt =
                            java.time.format.DateTimeFormatter
                                    .ofPattern("HH:mm");

                    java.time.LocalTime hora =
                            java.time.LocalTime.parse(entrada, fmt);

                    LocalDateTime dt =
                            LocalDateTime.now()
                                    .withHour(hora.getHour())
                                    .withMinute(hora.getMinute())
                                    .withSecond(0)
                                    .withNano(0);

                    if (dt.isBefore(LocalDateTime.now())) {
                        System.out.println(
                                "[ERROR] Esa hora ya pasó hoy."
                        );
                        return null;
                    }

                    long minutosRestantes =
                            java.time.Duration.between(
                                    LocalDateTime.now(), dt
                            ).toMinutes();

                    System.out.println(
                            "[INFO] Viaje programado para las "
                                    + entrada
                                    + " (en aprox. "
                                    + minutosRestantes
                                    + " minuto(s))."
                    );

                    return dt;
                }

                default:
                    System.out.println("[ERROR] Opción inválida.");
                    return null;
            }

        } catch (Exception e) {

            System.out.println(
                    "[ERROR] Formato inválido: "
                            + e.getMessage()
            );

            return null;
        }
    }

    // Consulta al servidor y retorna el primer viaje
    // que esté en un estado "ocupado" (EN_CURSO, PENDIENTE
    // o PROGRAMADO). Si no existe ninguno, retorna null.
    private static Viaje obtenerViajeActivo(String usuario) {

        MensajeUber respuesta =
                enviarPeticion(
                        new MensajeUber(
                                TipoMensaje.CONSULTAR_VIAJES,
                                usuario,
                                null
                        )
                );

        if (respuesta.getAccion() == TipoMensaje.ERROR) {
            return null;
        }

        @SuppressWarnings("unchecked")
        List<Viaje> lista =
                (List<Viaje>) respuesta.getPayload();

        for (Viaje v : lista) {
            EstadoViaje estado = v.getEstado();
            if (estado == EstadoViaje.EN_CURSO
                    || estado == EstadoViaje.PENDIENTE
                    || estado == EstadoViaje.PROGRAMADO) {
                return v;
            }
        }

        return null;
    }

    // Imprime todos los viajes del pasajero.
    private static void imprimirListaViajes(String usuario) {

        MensajeUber respuesta =
                enviarPeticion(
                        new MensajeUber(
                                TipoMensaje.CONSULTAR_VIAJES,
                                usuario,
                                null
                        )
                );

        @SuppressWarnings("unchecked")
        List<Viaje> lista =
                (List<Viaje>) respuesta.getPayload();

        System.out.println("\n=== LISTA DE VIAJES ===");

        if (lista.isEmpty()) {
            System.out.println("No existen viajes registrados.");
        } else {
            for (Viaje v : lista) {
                System.out.println(v);
            }
        }
    }

    private static MensajeUber enviarPeticion(
            MensajeUber peticion) {

        int intentos = 0;

        while (intentos < MAX_REINTENTOS) {

            try (
                    Socket socket =
                            new Socket(IP_SERVIDOR, PUERTO);

                    ObjectOutputStream out =
                            new ObjectOutputStream(
                                    socket.getOutputStream()
                            );

                    ObjectInputStream in =
                            new ObjectInputStream(
                                    socket.getInputStream()
                            )
            ) {

                socket.setSoTimeout(5000);

                out.writeObject(peticion);
                out.flush();

                Object primerObjeto = in.readObject();

                if (primerObjeto instanceof MensajeUber) {
                    MensajeUber mensaje = (MensajeUber) primerObjeto;

                    if (mensaje.getAccion() == TipoMensaje.ACK &&
                            peticion.getRequestId().equals(mensaje.getRequestId())) {

                        Object segundoObjeto = in.readObject();

                        if (segundoObjeto instanceof MensajeUber) {
                            MensajeUber respuesta = (MensajeUber) segundoObjeto;
                            if (peticion.getRequestId().equals(respuesta.getRequestId())) {
                                return respuesta;
                            }
                        }
                    } else if (mensaje.getRequestId() != null &&
                            mensaje.getRequestId().equals(peticion.getRequestId())) {

                        return mensaje;
                    }
                }

                throw new Exception("Respuesta inválida del servidor");

            } catch (Exception e) {

                intentos++;

                System.err.println(
                        "[CLIENTE] Intento " + intentos + ": "
                                + e.getMessage()
                );

                if (intentos >= MAX_REINTENTOS) {
                    break;
                }
            }
        }

        return new MensajeUber(
                TipoMensaje.ERROR,
                "CLIENTE",
                "No fue posible conectar con el servidor después de "
                        + MAX_REINTENTOS + " intentos"
        );
    }
}
