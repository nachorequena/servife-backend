package ar.edu.iessf.servife.reputacion.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo de D1: de 1 a 5 estrellas y un comentario opcional (vacío o solo espacios se guarda null). */
public record ValoracionRequest(
        @NotNull(message = "es obligatorio") @Min(value = 1, message = "tiene que ser de 1 a 5") @Max(value = 5, message = "tiene que ser de 1 a 5") Integer puntaje,
        @Size(max = 500, message = "tiene que tener como máximo 500 caracteres") String comentario) {
}
