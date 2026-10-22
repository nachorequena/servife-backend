package ar.edu.iessf.servife.identidad.dto;

import ar.edu.iessf.servife.common.seguridad.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * A2 · cuerpo del login. {@code rol} es opcional: solo hace falta cuando el email tiene una cuenta de
 * cliente y una de prestador y la app ya preguntó con cuál ingresar (409 ELEGIR_ROL, D43).
 */
public record LoginRequest(@NotBlank(message = "es obligatorio") @Email(message = "no es un correo válido") String email, @NotBlank(message = "es obligatorio") String contrasenia, Rol rol) {

    /** Recorta el email antes de validar: " Ana@Mail.com " es la misma cuenta que "Ana@Mail.com". */
    public LoginRequest {
        email = email == null ? null : email.strip();
    }

    /** Sin rol: el login de siempre. */
    public LoginRequest(String email, String contrasenia) {
        this(email, contrasenia, null);
    }
}
