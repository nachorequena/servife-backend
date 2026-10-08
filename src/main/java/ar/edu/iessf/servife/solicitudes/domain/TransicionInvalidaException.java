package ar.edu.iessf.servife.solicitudes.domain;

import ar.edu.iessf.servife.common.error.ConflictoException;

/** 409 TRANSICION_INVALIDA: la acción no existe para el estado actual de la solicitud. */
public class TransicionInvalidaException extends ConflictoException {

    public TransicionInvalidaException(EstadoSolicitud origen, AccionSobreSolicitud accion) {
        super("TRANSICION_INVALIDA", "No se puede " + accion.verbo() + " una solicitud " + origen.etiqueta() + ".");
    }
}
