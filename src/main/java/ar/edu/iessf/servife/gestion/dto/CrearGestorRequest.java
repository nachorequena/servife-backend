package ar.edu.iessf.servife.gestion.dto;

import ar.edu.iessf.servife.identidad.service.Contrasenias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** E13 · CU15 · Alta de un gestor (D06: es la única vía). Mismas reglas que el registro (A1). */
public record CrearGestorRequest(
    @NotBlank(message = "es obligatorio") @Size(max = 120, message = "es demasiado largo") String nombreApellido,
    @NotBlank(message = "es obligatorio") @Email(message = "no es un correo válido") @Size(max = 254, message = "es demasiado largo") String email,
    @NotBlank(message = "es obligatorio") @Pattern(regexp = Contrasenias.REGLA, message = Contrasenias.MENSAJE) String contrasenia) {

    /** Recorta el email antes de validar, como el registro. */
    public CrearGestorRequest {
        email = email == null ? null : email.strip();
    }
}
