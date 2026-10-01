package ar.edu.iessf.servife.catalogo.dto;

import java.util.UUID;

/** Rubro de servicio tal como lo ve la app. Nunca expone el id BIGINT. */
public record TipoServicioResponse(UUID uuid, String nombre, String icono, boolean requiereMatricula) {
}
