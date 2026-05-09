package uber.server;

import uber.shared.*;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;


public class ManejadorCliente implements Runnable {

    private Socket socketCliente;
    private GestorUber gestor;

    public ManejadorCliente(Socket socketCliente, GestorUber gestor) {
        this.socketCliente = socketCliente;
        this.gestor = gestor;
    }

    @Override
    public void run() {

        try (
                ObjectOutputStream out =
                        new ObjectOutputStream(socketCliente.getOutputStream());

                ObjectInputStream in =
                        new ObjectInputStream(socketCliente.getInputStream())
        ) {

            MensajeUber peticion =
                    (MensajeUber) in.readObject();

            System.out.println(
                    "[HILO " + Thread.currentThread().getId() + "] " +
                            "Petición: " +
                            peticion.getAccion() +
                            " de " +
                            peticion.getIdUsuario()
            );

            switch (peticion.getAccion()) {

                case SOLICITAR_VIAJE:

                    SolicitudViaje solicitudInmediata =
                            (SolicitudViaje) peticion.getPayload();

                    Viaje viajeInmediato =
                            gestor.solicitarViaje(
                                    peticion.getIdUsuario(),
                                    solicitudInmediata
                            );

                    out.writeObject(
                            new MensajeUber(
                                    TipoMensaje.RESPUESTA_VIAJE,
                                    "SERVIDOR",
                                    viajeInmediato
                            )
                    );

                    break;

                case PROGRAMAR_VIAJE:

                    SolicitudViaje solicitudProgramada =
                            (SolicitudViaje) peticion.getPayload();

                    Viaje viajeProgramado =
                            gestor.programarViaje(
                                    peticion.getIdUsuario(),
                                    solicitudProgramada
                            );

                    out.writeObject(
                            new MensajeUber(
                                    TipoMensaje.RESPUESTA_PROGRAMAR,
                                    "SERVIDOR",
                                    viajeProgramado
                            )
                    );

                    break;

                case CONSULTAR_VIAJES:

                    List<Viaje> viajes =
                            gestor.consultarViajes(
                                    peticion.getIdUsuario()
                            );

                    out.writeObject(
                            new MensajeUber(
                                    TipoMensaje.RESPUESTA_CONSULTA,
                                    "SERVIDOR",
                                    viajes
                            )
                    );

                    break;

                case FINALIZAR_VIAJE:

                    Integer idViaje =
                            (Integer) peticion.getPayload();

                    String resultado =
                            gestor.finalizarViaje(
                                    idViaje,
                                    peticion.getIdUsuario()
                            );

                    out.writeObject(
                            new MensajeUber(
                                    TipoMensaje.RESPUESTA_FINALIZAR,
                                    "SERVIDOR",
                                    resultado
                            )
                    );

                    break;

                default:

                    out.writeObject(
                            new MensajeUber(
                                    TipoMensaje.ERROR,
                                    "SERVIDOR",
                                    "Acción desconocida"
                            )
                    );
            }

        } catch (Exception e) {

            System.err.println(
                    "[HILO " +
                            Thread.currentThread().getId() +
                            "] Error con cliente: " +
                            e.getMessage()
            );

        } finally {

            try {
                socketCliente.close();
            } catch (Exception e) {
                // Ignorar
            }
        }
    }
}