package ar.edu.iessf.servife.solicitudes.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo de C1. Las imágenes son archivos ya subidos por el cliente (D7). */
public record CrearSolicitudRequest(
        @NotNull(message = "es obligatorio") UUID uuidPrestador,
        @NotNull(message = "es obligatoria") LocalDate fechaDeseada,
        @Size(max = 40, message = "tiene que tener como máximo 40 caracteres") String horaPreferida,
        @NotBlank(message = "es obligatoria") @Size(max = 255, message = "tiene que tener como máximo 255 caracteres") String direccion,
        @NotBlank(message = "es obligatoria") @Size(max = 2000, message = "tiene que tener como máximo 2000 caracteres") String descripcion,
        @Size(max = 5, message = "puede tener como máximo 5 imágenes") List<@NotNull(message = "no puede haber imágenes vacías") UUID> imagenIds) {
}
