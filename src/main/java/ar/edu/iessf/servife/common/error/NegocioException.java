package ar.edu.iessf.servife.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base de los errores que el servicio lanza a propósito. El ManejadorGlobalDeErrores la
 * convierte en ErrorRespuesta con este status y este código.
 * Para casos nuevos, preferí las subclases (RecursoNoEncontradoException, ConflictoException)
 * antes que crear otra.
 */
public class NegocioException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;

    public NegocioException(HttpStatus status, String codigo, String mensaje) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo;
    }
}
