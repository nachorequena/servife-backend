package ar.edu.iessf.servife.gestion.dto;

import java.time.Instant;
import java.util.UUID;

import ar.edu.iessf.servife.catalogo.dto.TipoServicioResponse;

/** Prestador esperando validación (E5). Nunca expone el id BIGINT. */
public record PrestadorPendienteResponse(UUID uuid, String nombreApellido, String email,
        TipoServicioResponse tipoServicio, Instant creadoEn) {
}
