package ar.edu.iessf.servife.gestion.dto;

import java.time.Instant;
import java.util.List;

/** Expediente de validación (E7b para el prestador, E8 para el gestor). declaracionJuradaEn null: todavía no la aceptó. */
public record ExpedienteResponse(Instant declaracionJuradaEn, List<DocumentoResponse> documentos) {
}
