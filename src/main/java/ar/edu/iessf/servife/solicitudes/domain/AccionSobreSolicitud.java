package ar.edu.iessf.servife.solicitudes.domain;

/** Acciones que un usuario puede pedir sobre una solicitud (cuerpo de C4). */
public enum AccionSobreSolicitud {
    ACEPTAR("aceptar"),
    RECHAZAR("rechazar"),
    INICIAR("iniciar"),
    FINALIZAR("finalizar"),
    CANCELAR("cancelar"),
    /** D07: el cliente da por realizado el trabajo sin esperar al prestador (ACEPTADA o EN_CURSO → FINALIZADA). */
    CONFIRMAR_REALIZADO("confirmar como realizada"),
    /** D07: el cliente informa que el prestador no se presentó (ACEPTADA → CANCELADA con motivo fijo). */
    NO_SE_PRESENTO("marcar como ausente al prestador de");

    private final String verbo;

    AccionSobreSolicitud(String verbo) {
        this.verbo = verbo;
    }

    /** Acciones que dependen de que haya llegado la fecha deseada (hora de Argentina): D26 y D07. */
    public boolean requiereLlegarLaFecha() {
        return this == INICIAR || this == CONFIRMAR_REALIZADO || this == NO_SE_PRESENTO;
    }

    /** Infinitivo en minúsculas para armar mensajes ("No se puede aceptar..."). */
    public String verbo() {
        return verbo;
    }
}
