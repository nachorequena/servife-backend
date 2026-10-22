package ar.edu.iessf.servife.gestion.dto;

import java.util.UUID;

import ar.edu.iessf.servife.solicitudes.domain.EstadoSolicitud;

/**
 * Una fila de la bandeja Mensajes (E12). {@code ultimoMensaje} es null si todavía nadie escribió;
 * {@code noLeidos} cuenta los mensajes de la otra parte que el usuario no abrió.
 */
public record ConversacionResponse(UUID uuidChat, UUID uuidSolicitud, EstadoSolicitud estadoSolicitud,
        String tipoServicio, ContraparteResponse contraparte, UltimoMensajeResponse ultimoMensaje, long noLeidos) {
}
