package ar.edu.iessf.servife.identidad.dto;

import java.util.UUID;

import ar.edu.iessf.servife.identidad.service.Contrasenias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A1 · cuerpo del registro. {@code idTipoServicio} (uuid) solo se usa si el rol es PRESTADOR. */
public record RegistroRequest(
    @NotBlank @Pattern(regexp = "CLIENTE|PRESTADOR", message = "tiene que ser CLIENTE o PRESTADOR") String rol,
    @NotBlank @Size(max = 120) String nombreApellido,
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Pattern(regexp = Contrasenias.REGLA, message = Contrasenias.MENSAJE) String contrasenia,
    UUID idTipoServicio) {
}
