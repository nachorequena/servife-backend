package ar.edu.iessf.servife.identidad.dto;

import ar.edu.iessf.servife.identidad.service.Contrasenias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** A9 · cuerpo de la confirmación: el código recibido por correo y la contraseña nueva. */
public record ConfirmarRecuperacionRequest(
    @NotBlank @Email String email,
    @NotBlank @Pattern(regexp = "\\d{6}", message = "son 6 dígitos") String codigo,
    @NotBlank @Pattern(regexp = Contrasenias.REGLA, message = Contrasenias.MENSAJE) String contraseniaNueva) {

    /** Recorta el email antes de validar, igual que en el login y el registro. */
    public ConfirmarRecuperacionRequest {
        email = email == null ? null : email.strip();
    }
}
