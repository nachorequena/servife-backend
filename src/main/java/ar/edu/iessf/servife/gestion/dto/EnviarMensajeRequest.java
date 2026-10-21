package ar.edu.iessf.servife.gestion.dto;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo de E2. El largo (1000) se valida en el servicio sobre el texto ya recortado. */
public record EnviarMensajeRequest(@NotBlank(message = "es obligatorio") String texto) {
}
