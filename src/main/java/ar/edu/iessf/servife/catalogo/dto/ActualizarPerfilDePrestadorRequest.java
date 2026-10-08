package ar.edu.iessf.servife.catalogo.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** B7 · Rubro, zona, punto de trabajo (lat y lng van juntas o ninguna), radio y descripción. Sin tarifa (D02). */
public record ActualizarPerfilDePrestadorRequest(
    @NotNull(message = "Elegí el rubro de tu servicio") UUID idTipoServicio,
    @Size(max = 120, message = "La zona puede tener hasta 120 caracteres") String zona,
    @DecimalMin(value = "-90", message = "La latitud tiene que estar entre -90 y 90")
    @DecimalMax(value = "90", message = "La latitud tiene que estar entre -90 y 90") BigDecimal lat,
    @DecimalMin(value = "-180", message = "La longitud tiene que estar entre -180 y 180")
    @DecimalMax(value = "180", message = "La longitud tiene que estar entre -180 y 180") BigDecimal lng,
    @Min(value = 1, message = "El radio tiene que ser de al menos 1 km")
    @Max(value = 100, message = "El radio puede ser de hasta 100 km") Integer radioKm,
    @Size(max = 2000, message = "La descripción puede tener hasta 2000 caracteres") String descripcion) {
}
