package ar.edu.iessf.servife.gestion.dto;

/** Solicitudes creadas en un mes (mes con formato yyyy-MM, hora de Argentina). Los meses sin solicitudes vienen en 0. */
public record ConteoPorMes(String mes, long cantidad) {
}
