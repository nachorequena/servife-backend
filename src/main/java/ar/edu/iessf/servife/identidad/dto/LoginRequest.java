package ar.edu.iessf.servife.identidad.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** A2 · cuerpo del login. */
public record LoginRequest(@NotBlank @Email String email, @NotBlank String contrasenia) {

    /** Recorta el email antes de validar: " Ana@Mail.com " es la misma cuenta que "Ana@Mail.com". */
    public LoginRequest {
        email = email == null ? null : email.strip();
    }
}
