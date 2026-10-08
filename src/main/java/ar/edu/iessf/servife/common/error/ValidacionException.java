package ar.edu.iessf.servife.common.error;

/**
 * 400 VALIDACION por una regla que Bean Validation no alcanza a expresar (depende de la base o de
 * otro campo). Responde con el mismo formato que los errores de campo: un solo ErrorCampo.
 */
public class ValidacionException extends RuntimeException {

    private final String campo;
    private final String detalle;

    public ValidacionException(String campo, String detalle) {
        super(campo + ": " + detalle);
        this.campo = campo;
        this.detalle = detalle;
    }

    public String getCampo() {
        return campo;
    }

    public String getDetalle() {
        return detalle;
    }
}
