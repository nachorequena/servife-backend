package ar.edu.iessf.servife.catalogo.dto;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** B8 · Días de la semana en que trabaja (1 = lunes ... 7 = domingo). Reemplaza el set completo; vacío = ninguno. */
public record DisponibilidadRequest(
    @NotNull(message = "Indicá los días en que trabajás")
    @Size(max = 7, message = "No puede haber más de 7 días")
    List<@NotNull(message = "Cada día tiene que ser un número del 1 al 7")
        @Min(value = 1, message = "Los días van del 1 (lunes) al 7 (domingo)")
        @Max(value = 7, message = "Los días van del 1 (lunes) al 7 (domingo)") Integer> dias) {
}
