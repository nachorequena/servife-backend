package ar.edu.iessf.servife.gestion.dto;

import java.time.Instant;
import java.util.UUID;

/** Un mensaje del chat (E1, E2). {@code propio} es true si lo escribió el usuario del token. */
public record MensajeResponse(UUID uuid, String texto, boolean propio, Instant creadoEn) {
}
