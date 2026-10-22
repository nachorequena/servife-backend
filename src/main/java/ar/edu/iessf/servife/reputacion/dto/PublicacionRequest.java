package ar.edu.iessf.servife.reputacion.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de D4 y D5 (D5 reemplaza todo). uuidSolicitud vincula una solicitud terminada propia (D07);
 * las imágenes son archivos ya subidos por el prestador (D7).
 */
public record PublicacionRequest(
        @NotBlank(message = "es obligatorio") @Size(max = 120, message = "tiene que tener como máximo 120 caracteres") String titulo,
        @Size(max = 2000, message = "tiene que tener como máximo 2000 caracteres") String descripcion,
        LocalDate fechaTrabajo,
        UUID uuidSolicitud,
        @Size(max = 5, message = "puede tener como máximo 5 imágenes") List<@NotNull(message = "no puede haber imágenes vacías") UUID> imagenIds) {
}
