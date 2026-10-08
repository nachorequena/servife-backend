package ar.edu.iessf.servife.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de B2 y B3. El ícono debe estar en {@code IconosDeServicio.PERMITIDOS}. */
public record TipoServicioRequest(
        @NotBlank(message = "Ingresá el nombre.") @Size(max = 80, message = "Usá hasta 80 caracteres.") String nombre,
        @NotBlank(message = "Elegí un ícono.") String icono,
        boolean requiereMatricula) {
}
