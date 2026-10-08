package ar.edu.iessf.servife.identidad.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

/** A6 · Datos editables de la cuenta. El gestor solo puede cambiar nombreApellido. */
public record ActualizarUsuarioRequest(@NotBlank(message = "es obligatorio") @Size(max = 120, message = "es demasiado largo") String nombreApellido,
    @Size(max = 30, message = "es demasiado largo") String telefono, @Size(max = 255, message = "es demasiado largo") String direccion, @Past(message = "tiene que ser una fecha pasada") LocalDate fecNacimiento) {
}
