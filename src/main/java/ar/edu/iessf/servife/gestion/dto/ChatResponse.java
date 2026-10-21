package ar.edu.iessf.servife.gestion.dto;

import java.util.List;
import java.util.UUID;

import ar.edu.iessf.servife.solicitudes.domain.EstadoSolicitud;

/**
 * E1: el chat de una solicitud. {@code mensajes} son todos, o solo los posteriores a ?desde=.
 * {@code sePuedeEscribir} es false desde FINALIZADA y en RECHAZADA o CANCELADA (D33).
 */
public record ChatResponse(UUID uuidChat, UUID uuidSolicitud, EstadoSolicitud estadoSolicitud,
        boolean sePuedeEscribir, ContraparteResponse contraparte, List<MensajeResponse> mensajes) {
}
