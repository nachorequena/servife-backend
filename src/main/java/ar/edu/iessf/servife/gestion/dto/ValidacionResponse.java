package ar.edu.iessf.servife.gestion.dto;

import java.util.UUID;

import ar.edu.iessf.servife.identidad.domain.EstadoValidacion;

/** Resultado de E6: el prestador y su nuevo estado de validación. */
public record ValidacionResponse(UUID uuid, EstadoValidacion estadoValidacion) {
}
