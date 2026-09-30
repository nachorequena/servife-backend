package ar.edu.iessf.servife.common.error;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Escribe el formato único de error para lo que se corta en el filtro de seguridad,
 * antes de llegar a un controller (y por eso no pasa por el ManejadorGlobalDeErrores).
 */
@Component
public class EscritorDeErrores {

    private final ObjectMapper objectMapper;

    public EscritorDeErrores(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void noAutenticado(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        escribir(response, ErrorRespuesta.de(HttpStatus.UNAUTHORIZED.value(), "NO_AUTENTICADO",
            "Falta el token o está vencido.", request.getRequestURI()));
    }

    public void accesoDenegado(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException {
        escribir(response, ErrorRespuesta.de(HttpStatus.FORBIDDEN.value(), "ACCESO_DENEGADO",
            "Tu rol no permite esta operación.", request.getRequestURI()));
    }

    private void escribir(HttpServletResponse response, ErrorRespuesta error) throws IOException {
        response.setStatus(error.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
