package ar.edu.iessf.servife.common.error;

import java.time.Instant;
import java.util.List;

/**
 * Formato único de error de toda la API (.ai/05-api-contract.md).
 * errores va vacío salvo en los 400 de validación.
 */
public record ErrorRespuesta(
        Instant timestamp,
        int status,
        String codigo,
        String mensaje,
        String path,
        List<ErrorCampo> errores) {

    public static ErrorRespuesta de(int status, String codigo, String mensaje, String path) {
        return new ErrorRespuesta(Instant.now(), status, codigo, mensaje, path, List.of());
    }

    public static ErrorRespuesta de(int status, String codigo, String mensaje, String path, List<ErrorCampo> errores) {
        return new ErrorRespuesta(Instant.now(), status, codigo, mensaje, path, errores);
    }
}
