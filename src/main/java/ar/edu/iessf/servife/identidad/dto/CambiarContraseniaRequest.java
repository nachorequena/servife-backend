package ar.edu.iessf.servife.identidad.dto;

import ar.edu.iessf.servife.identidad.service.Contrasenias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** A7 · Cambio de contraseña verificando la actual. */
public record CambiarContraseniaRequest(@NotBlank String contraseniaActual,
    @NotBlank @Pattern(regexp = Contrasenias.REGLA, message = Contrasenias.MENSAJE) String contraseniaNueva) {
}
