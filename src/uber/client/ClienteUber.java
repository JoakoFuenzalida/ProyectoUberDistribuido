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

            System.out.println("\n--------- MENÚ ---------");
            System.out.println("1. Solicitar viaje");
            System.out.println("2. Programar viaje");
            System.out.println("3. Consultar viajes");
            System.out.println("4. Finalizar viaje");
            System.out.println("5. Salir");

            System.out.print("Seleccione opción: ");

            String opcion = scanner.nextLine();

            switch (opcion) {

                case "1":

                    System.out.print("Origen: ");
                    String origen = scanner.nextLine();

                    System.out.print("Destino: ");
                    String destino = scanner.nextLine();

                    SolicitudViaje solicitud =
                            new SolicitudViaje(
                                    origen,
                                    destino,
                                    false,
                                    null
                            );

                    MensajeUber respuesta =
                            enviarPeticion(
                                    new MensajeUber(
                                            TipoMensaje.SOLICITAR_VIAJE,
                                            usuario,
                                            solicitud
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

                    System.out.println(
                            respuesta.getPayload()
                    );

                    break;

                case "2":

                    System.out.print("Origen: ");
                    String origenProgramado =
                            scanner.nextLine();

                    System.out.print("Destino: ");
                    String destinoProgramado =
                            scanner.nextLine();

                    System.out.print(
                            "¿Cuántos segundos desde ahora desea programarlo?: "
                    );

                    int segundos =
                            Integer.parseInt(scanner.nextLine());

                    LocalDateTime fecha =
                            LocalDateTime.now()
                                    .plusSeconds(segundos);

                    SolicitudViaje solicitudProgramada =
                            new SolicitudViaje(
                                    origenProgramado,
                                    destinoProgramado,
                                    true,
                                    fecha
                            );

                    MensajeUber respuestaProgramada =
                            enviarPeticion(
                                    new MensajeUber(
                                            TipoMensaje.PROGRAMAR_VIAJE,
                                            usuario,
                                            solicitudProgramada
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

                    break;

                case "3":

                    MensajeUber respuestaConsulta =
                            enviarPeticion(
                                    new MensajeUber(
                                            TipoMensaje.CONSULTAR_VIAJES,
                                            usuario,
                                            null
                                    )
                            );

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

                    System.out.println(
                            "\n=== LISTA DE VIAJES ==="
                    );

                    if (viajes.isEmpty()) {

                        System.out.println(
                                "No existen viajes registrados."
                        );

                    } else {

                        for (Viaje v : viajes) {
                            System.out.println(v);
                        }
                    }

                    break;

                case "4":

                    System.out.print(
                            "Ingrese ID del viaje a finalizar: "
                    );

                    int idViaje =
                            Integer.parseInt(scanner.nextLine());

                    MensajeUber respuestaFinalizar =
                            enviarPeticion(
                                    new MensajeUber(
                                            TipoMensaje.FINALIZAR_VIAJE,
                                            usuario,
                                            idViaje
                                    )
                            );

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
                            respuestaFinalizar.getPayload()
                    );

                    break;

                case "5":

                    System.out.println(
                            "Cerrando cliente..."
                    );

                    scanner.close();

                    System.exit(0);

                default:

                    System.out.println(
                            "Opción inválida."
                    );
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
