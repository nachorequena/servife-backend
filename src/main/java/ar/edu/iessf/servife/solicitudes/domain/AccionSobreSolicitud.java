package ar.edu.iessf.servife.solicitudes.domain;

/** Acciones que un usuario puede pedir sobre una solicitud (cuerpo de C4). */
public enum AccionSobreSolicitud {
    ACEPTAR("aceptar"),
    RECHAZAR("rechazar"),
    INICIAR("iniciar"),
    FINALIZAR("finalizar"),
    CANCELAR("cancelar");

    private final String verbo;

    AccionSobreSolicitud(String verbo) {
        this.verbo = verbo;
    }

    /** Infinitivo en minúsculas para armar mensajes ("No se puede aceptar..."). */
    public String verbo() {
        return verbo;
    }
}
