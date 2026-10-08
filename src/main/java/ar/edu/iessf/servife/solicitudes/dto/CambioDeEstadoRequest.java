package ar.edu.iessf.servife.solicitudes.dto;

import ar.edu.iessf.servife.solicitudes.domain.AccionSobreSolicitud;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de C4. motivo solo con RECHAZAR o CANCELAR; precioAcordado (en centavos) solo con ACEPTAR.
 */
public record CambioDeEstadoRequest(
    @NotNull(message = "es obligatoria") AccionSobreSolicitud accion,
    @Size(max = 255, message = "tiene que tener como máximo 255 caracteres") String motivo,
    @PositiveOrZero(message = "no puede ser negativo") Long precioAcordado) {
}
