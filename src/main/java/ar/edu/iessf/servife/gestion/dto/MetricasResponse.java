package ar.edu.iessf.servife.gestion.dto;

/**
 * Contadores del dashboard (E9, D05). serviciosActivos: solicitudes ACEPTADA o EN_CURSO;
 * solicitudesPendientes: esperando respuesta del prestador; validacionesPendientes: los mismos que lista E5.
 */
public record MetricasResponse(long serviciosActivos, long solicitudesPendientes, long validacionesPendientes) {
}
