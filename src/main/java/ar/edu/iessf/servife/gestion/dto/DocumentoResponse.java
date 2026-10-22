package ar.edu.iessf.servife.gestion.dto;

import java.time.Instant;
import java.util.UUID;

import ar.edu.iessf.servife.gestion.domain.TipoDeDocumento;
import ar.edu.iessf.servife.identidad.domain.EstadoValidacion;

/** Un documento del expediente (E7, E7b, E8). Solo metadatos: los bytes los sirve E8b, solo al gestor. */
public record DocumentoResponse(UUID uuid, TipoDeDocumento tipo, EstadoValidacion estado, Instant creadoEn) {
}
