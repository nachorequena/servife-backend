package ar.edu.iessf.servife.solicitudes.domain;

/** Estados de una solicitud (columna estado de solicitudes_servicio). VALORADA se usa desde el Sprint 4. */
public enum EstadoSolicitud {
    PENDIENTE("pendiente"),
    ACEPTADA("aceptada"),
    RECHAZADA("rechazada"),
    CANCELADA("cancelada"),
    EN_CURSO("en curso"),
    FINALIZADA("finalizada"),
    VALORADA("valorada");

    private final String etiqueta;

    EstadoSolicitud(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Texto en minúsculas para armar mensajes ("una solicitud en curso"). */
    public String etiqueta() {
        return etiqueta;
    }
}
