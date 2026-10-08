package ar.edu.iessf.servife.gestion.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo de E6. El motivo es opcional para ambas decisiones; en blanco equivale a no enviarlo. */
public record DecisionDeValidacionRequest(
    @NotNull(message = "Indicá si aprobás o rechazás.") Decision decision,
    @Size(max = 255, message = "El motivo no puede superar los 255 caracteres.") String motivo) {
}
