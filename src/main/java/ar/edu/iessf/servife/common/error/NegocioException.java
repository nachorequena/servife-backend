package ar.edu.iessf.servife.common.error;

import java.util.List;

import org.springframework.http.HttpStatus;

/**
 * Base de los errores que el servicio lanza a propósito. El ManejadorGlobalDeErrores la
 * convierte en ErrorRespuesta con este status y este código.
 * Para casos nuevos, preferí las subclases (RecursoNoEncontradoException, ConflictoException)
 * antes que crear otra. Opcionalmente lleva los campos que originaron el error (ErrorCampo),
 * para que la app los marque en el formulario.
 */
public class NegocioException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;
    private final List<ErrorCampo> errores;

    public NegocioException(HttpStatus status, String codigo, String mensaje) {
        this(status, codigo, mensaje, List.of());
    }

    public NegocioException(HttpStatus status, String codigo, String mensaje, List<ErrorCampo> errores) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
        this.errores = List.copyOf(errores);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo;
    }

    public List<ErrorCampo> getErrores() {
        return errores;
    }
}
