package ar.edu.iessf.servife.common.error;

import org.springframework.http.HttpStatus;

/**
 * 404. También cuando el recurso existe pero no es del usuario autenticado:
 * no se revela que existe (.ai/07-security.md).
 */
public class RecursoNoEncontradoException extends NegocioException {

    public RecursoNoEncontradoException(String mensaje) {
        super(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", mensaje);
    }
}
