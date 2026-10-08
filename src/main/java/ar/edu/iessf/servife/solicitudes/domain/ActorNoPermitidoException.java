package ar.edu.iessf.servife.solicitudes.domain;

import org.springframework.http.HttpStatus;

import ar.edu.iessf.servife.common.error.NegocioException;

/** 403 ACCESO_DENEGADO: la acción existe para ese estado, pero no para el rol del actor. */
public class ActorNoPermitidoException extends NegocioException {

    public ActorNoPermitidoException(AccionSobreSolicitud accion) {
        super(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO", "Tu rol no permite " + accion.verbo() + " esta solicitud.");
    }
}
