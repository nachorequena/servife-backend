package ar.edu.iessf.servife.gestion.dto;

/** Una barra o porción de un gráfico: un nombre (estado, cliente, prestador) y cuántas solicitudes tiene. */
public record ConteoPorNombre(String nombre, long cantidad) {
}
