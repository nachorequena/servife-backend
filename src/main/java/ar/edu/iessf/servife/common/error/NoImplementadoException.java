package ar.edu.iessf.servife.common.error;

import org.springframework.http.HttpStatus;

/**
 * 501 de los endpoints que todavía son stubs. Cuando el dueño del módulo implementa el
 * endpoint, borra el throw. Cuando no quede ningún uso, se borra esta clase.
 */
public class NoImplementadoException extends NegocioException {

    public NoImplementadoException(String idEndpoint) {
        super(HttpStatus.NOT_IMPLEMENTED, "NO_IMPLEMENTADO", "Endpoint " + idEndpoint + " todavía no implementado.");
    }
}
