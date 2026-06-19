package uber.shared;

import java.io.Serializable;
import java.util.UUID;

public class MensajeUber implements Serializable {

    private static final long serialVersionUID = 1L;

    private TipoMensaje accion;
    private String idUsuario;
    private Object payload;
    private String requestId;
    private int relojLogico;

    public MensajeUber(TipoMensaje accion,
                       String idUsuario,
                       Object payload) {

        this(accion, idUsuario, payload, null, 0);
    }

    public MensajeUber(TipoMensaje accion,
                       String idUsuario,
                       Object payload,
                       String requestId,
                       int relojLogico) {

        this.accion = accion;
        this.idUsuario = idUsuario;
        this.payload = payload;
        this.requestId = (requestId == null || requestId.isEmpty())
                ? UUID.randomUUID().toString()
                : requestId;
        this.relojLogico = relojLogico;
    }

    public TipoMensaje getAccion() {
        return accion;
    }

    public String getIdUsuario() {
        return idUsuario;
    }

    public Object getPayload() {
        return payload;
    }

    public String getRequestId() {
        return requestId;
    }

    public int getRelojLogico() { return relojLogico; }
}
