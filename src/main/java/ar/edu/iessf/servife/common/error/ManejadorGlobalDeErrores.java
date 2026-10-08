package ar.edu.iessf.servife.common.error;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Único lugar donde una excepción se convierte en respuesta HTTP (.ai/05-api-contract.md).
 * Los módulos lanzan NegocioException o sus subclases; no arman ResponseEntity de error.
 */
@RestControllerAdvice
public class ManejadorGlobalDeErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalDeErrores.class);

    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<ErrorRespuesta> negocio(NegocioException e, HttpServletRequest request) {
        return ResponseEntity.status(e.getStatus()).body(ErrorRespuesta.de(e.getStatus().value(), e.getCodigo(),
            e.getMessage(), request.getRequestURI(), e.getErrores()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorRespuesta> validacion(MethodArgumentNotValidException e, HttpServletRequest request) {
        List<ErrorCampo> errores = e.getBindingResult().getFieldErrors().stream()
            // Un valor que no se pudo convertir (lat=abc) trae el texto de Spring en inglés: lo reemplazamos.
            .map(f -> new ErrorCampo(f.getField(), f.isBindingFailure() ? "tiene un formato inválido" : f.getDefaultMessage()))
            .toList();
        ErrorRespuesta cuerpo = ErrorRespuesta.de(HttpStatus.BAD_REQUEST.value(), "VALIDACION",
            "Hay campos con errores.", request.getRequestURI(), errores);
        return ResponseEntity.badRequest().body(cuerpo);
    }

    /** Regla de validación que depende de la base u otro campo: mismo formato que Bean Validation. */
    @ExceptionHandler(ValidacionException.class)
    public ResponseEntity<ErrorRespuesta> validacionDeNegocio(ValidacionException e, HttpServletRequest request) {
        ErrorRespuesta cuerpo = ErrorRespuesta.de(HttpStatus.BAD_REQUEST.value(), "VALIDACION",
            "Hay campos con errores.", request.getRequestURI(),
            List.of(new ErrorCampo(e.getCampo(), e.getDetalle())));
        return ResponseEntity.badRequest().body(cuerpo);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorRespuesta> cuerpoInvalido(Exception e, HttpServletRequest request) {
        return responder(HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA", "La solicitud no tiene el formato esperado.", request);
    }

    /** @PreAuthorize rechaza dentro del controller: sin este handler terminaría en 500. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorRespuesta> accesoDenegado(AccessDeniedException e, HttpServletRequest request) {
        return responder(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO", "Tu rol no permite esta operación.", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorRespuesta> archivoGrande(MaxUploadSizeExceededException e, HttpServletRequest request) {
        return responder(HttpStatus.PAYLOAD_TOO_LARGE, "ARCHIVO_DEMASIADO_GRANDE", "El archivo supera el tamaño máximo.", request);
    }

    /** Falta una parte multipart o un parámetro obligatorio. */
    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorRespuesta> faltaCampo(Exception e, HttpServletRequest request) {
        String nombre = e instanceof MissingServletRequestPartException p ? p.getRequestPartName()
            : ((MissingServletRequestParameterException) e).getParameterName();
        ErrorRespuesta cuerpo = ErrorRespuesta.de(HttpStatus.BAD_REQUEST.value(), "VALIDACION",
            "Hay campos con errores.", request.getRequestURI(), List.of(new ErrorCampo(nombre, "es obligatorio")));
        return ResponseEntity.badRequest().body(cuerpo);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorRespuesta> tipoDeContenido(HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        return responder(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "TIPO_DE_CONTENIDO_NO_SOPORTADO",
            "El tipo de contenido no es válido.", request);
    }

    /** Multipart mal formado (el caso de tamaño tiene su propio handler, más específico). */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorRespuesta> multipartInvalido(MultipartException e, HttpServletRequest request) {
        return responder(HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA", "La solicitud no tiene el formato esperado.", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorRespuesta> rutaInexistente(NoResourceFoundException e, HttpServletRequest request) {
        return responder(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "No existe.", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorRespuesta> metodoNoPermitido(HttpRequestMethodNotSupportedException e,
            HttpServletRequest request) {
        return responder(HttpStatus.METHOD_NOT_ALLOWED, "METODO_NO_PERMITIDO", "Método no permitido.", request);
    }

    /** Lo inesperado: se loguea (sin datos sensibles) y al cliente no se le filtra el detalle. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorRespuesta> inesperado(Exception e, HttpServletRequest request) {
        log.error("Error no manejado en {}", request.getRequestURI(), e);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", "Ocurrió un error inesperado.", request);
    }

    private ResponseEntity<ErrorRespuesta> responder(HttpStatus status, String codigo, String mensaje,
            HttpServletRequest request) {
        return ResponseEntity.status(status)
            .body(ErrorRespuesta.de(status.value(), codigo, mensaje, request.getRequestURI()));
    }
}
