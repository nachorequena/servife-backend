package ar.edu.iessf.servife.identidad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A10/A11 · Alias ("Casa", "Trabajo") y la dirección en texto libre. principal: opcional, true la marca como principal. */
public record DireccionRequest(
    @NotBlank(message = "es obligatorio") @Size(max = 40, message = "es demasiado largo") String alias,
    @NotBlank(message = "es obligatoria") @Size(max = 255, message = "es demasiado larga") String direccion,
    Boolean principal) {
}
