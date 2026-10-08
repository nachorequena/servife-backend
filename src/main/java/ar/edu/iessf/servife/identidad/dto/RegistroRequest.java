package ar.edu.iessf.servife.identidad.dto;

import java.util.UUID;

import ar.edu.iessf.servife.identidad.service.Contrasenias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A1 · cuerpo del registro. {@code idTipoServicio} (uuid) solo se usa si el rol es PRESTADOR. */
public record RegistroRequest(
    @NotBlank(message = "es obligatorio") @Pattern(regexp = "CLIENTE|PRESTADOR", message = "tiene que ser CLIENTE o PRESTADOR") String rol,
    @NotBlank(message = "es obligatorio") @Size(max = 120, message = "es demasiado largo") String nombreApellido,
    @NotBlank(message = "es obligatorio") @Email(message = "no es un correo válido") @Size(max = 254, message = "es demasiado largo") String email,
    @NotBlank(message = "es obligatorio") @Pattern(regexp = Contrasenias.REGLA, message = Contrasenias.MENSAJE) String contrasenia,
    UUID idTipoServicio) {

    /** Recorta el email antes de validar: " Ana@Mail.com " es la misma cuenta que "Ana@Mail.com". */
    public RegistroRequest {
        email = email == null ? null : email.strip();
    }
}
