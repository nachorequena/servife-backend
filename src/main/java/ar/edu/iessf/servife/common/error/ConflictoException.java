package ar.edu.iessf.servife.common.error;

import org.springframework.http.HttpStatus;

/** 409, conflicto de negocio. Ej.: TRANSICION_INVALIDA, EMAIL_YA_REGISTRADO. */
public class ConflictoException extends NegocioException {

    public ConflictoException(String codigo, String mensaje) {
        super(HttpStatus.CONFLICT, codigo, mensaje);
    }
}
