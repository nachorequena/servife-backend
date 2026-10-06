package ar.edu.iessf.servife.identidad.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** A8 · cuerpo del pedido de código de recuperación. */
public record RecuperarRequest(@NotBlank @Email String email) {

    /** Recorta el email antes de validar: " Ana@Mail.com " es la misma cuenta que "Ana@Mail.com". */
    public RecuperarRequest {
        email = email == null ? null : email.strip();
    }
}
