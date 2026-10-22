package ar.edu.iessf.servife.gestion.dto;

import java.time.Instant;

/** El último mensaje de un chat, para la bandeja (E12). */
public record UltimoMensajeResponse(String texto, Instant creadoEn, boolean propio) {
}
