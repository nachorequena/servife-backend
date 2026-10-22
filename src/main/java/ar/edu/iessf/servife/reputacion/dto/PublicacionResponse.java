package ar.edu.iessf.servife.reputacion.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Un trabajo realizado (D3, D4, D5, D8). puntaje es el de la valoración de la solicitud vinculada, o null
 * si no hay solicitud o todavía no la valoraron (D07: sin solicitud, sin estrellas). uuidSolicitud solo
 * viaja al dueño (D4, D5, D8); en D3 va null.
 */
public record PublicacionResponse(UUID uuid, String titulo, String descripcion, LocalDate fechaTrabajo,
        List<UUID> imagenIds, Integer puntaje, UUID uuidSolicitud, Instant creadoEn) {
}
