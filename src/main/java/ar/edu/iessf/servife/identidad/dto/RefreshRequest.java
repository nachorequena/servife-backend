package ar.edu.iessf.servife.identidad.dto;

import jakarta.validation.constraints.NotBlank;

/** A3 · cuerpo del refresh. */
public record RefreshRequest(@NotBlank String refreshToken) {
}
