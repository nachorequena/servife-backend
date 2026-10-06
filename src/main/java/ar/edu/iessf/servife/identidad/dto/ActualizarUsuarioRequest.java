package ar.edu.iessf.servife.identidad.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

/** A6 · Datos editables de la cuenta. El gestor solo puede cambiar nombreApellido. */
public record ActualizarUsuarioRequest(@NotBlank @Size(max = 120) String nombreApellido,
    @Size(max = 30) String telefono, @Size(max = 255) String direccion, @Past LocalDate fecNacimiento) {
}
