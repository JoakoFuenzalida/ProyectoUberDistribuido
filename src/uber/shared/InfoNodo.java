package uber.shared;

import java.io.Serializable;

public class InfoNodo implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final int puerto;

    public InfoNodo(String id, int puerto) {
        this.id = id;
        this.puerto = puerto;
    }

    public String getId() {
        return id;
    }

    public int getPuerto() {
        return puerto;
    }
}
