package ar.edu.iessf.servife.gestion.dto;

import ar.edu.iessf.servife.identidad.domain.EstadoCuenta;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** E4 · CU12 · Suspender (motivo obligatorio) o reactivar (motivo opcional). El motivo va en el correo al usuario. */
public record CambioDeEstadoDeCuentaRequest(
    @NotNull(message = "Indicá ACTIVA o SUSPENDIDA.") EstadoCuenta estado,
    @Size(max = 255, message = "El motivo no puede superar los 255 caracteres.") String motivo) {
}
