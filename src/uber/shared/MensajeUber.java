package uber.shared;

import java.io.Serializable;

public class MensajeUber implements Serializable {

    private static final long serialVersionUID = 1L;

    private TipoMensaje accion;
    private String idUsuario;
    private Object payload;

    public MensajeUber(TipoMensaje accion,
                       String idUsuario,
                       Object payload) {

        this.accion = accion;
        this.idUsuario = idUsuario;
        this.payload = payload;
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
}
