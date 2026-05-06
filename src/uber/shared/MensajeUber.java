package uber.shared;
import java.io.Serializable;

public class MensajeUber implements Serializable {
    private static final long serialVersionUID = 1L;
    private String accion;
    private String idUsuario;
    private Object payload;

    public MensajeUber(String accion, String idUsuario, Object payload) {
        this.accion = accion;
        this.idUsuario = idUsuario;
        this.payload = payload;
    }

    public String getAccion() { return accion; }
    public String getIdUsuario() { return idUsuario; }
    public Object getPayload() { return payload; }
}
