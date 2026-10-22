package ar.edu.iessf.servife.reputacion.dto;

import java.time.Instant;
import java.util.UUID;

/** Una reseña del perfil del prestador (D2): puntaje de 1 a 5, comentario y quién la escribió. */
public record ValoracionResponse(UUID uuid, int puntaje, String comentario, String nombreCliente, Instant creadoEn) {
}
