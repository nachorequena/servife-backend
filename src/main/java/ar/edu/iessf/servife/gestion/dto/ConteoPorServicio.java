package ar.edu.iessf.servife.gestion.dto;

/** Trabajo por tipo de servicio: solicitudes recibidas y cuántas quedaron realizadas (FINALIZADA o VALORADA). */
public record ConteoPorServicio(String servicio, long solicitudes, long realizados) {
}
