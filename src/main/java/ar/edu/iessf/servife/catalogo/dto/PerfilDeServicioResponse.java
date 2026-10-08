package ar.edu.iessf.servife.catalogo.dto;

import java.math.BigDecimal;
import java.util.UUID;

import ar.edu.iessf.servife.identidad.domain.EstadoValidacion;

/** Perfil de servicio del prestador (B7 y GET /prestadores/me/perfil). Nunca expone ids BIGINT. */
public record PerfilDeServicioResponse(UUID idTipoServicio, TipoServicioResponse tipoServicio, String zona,
    BigDecimal lat, BigDecimal lng, Integer radioKm, String descripcion, EstadoValidacion estadoValidacion) {
}
