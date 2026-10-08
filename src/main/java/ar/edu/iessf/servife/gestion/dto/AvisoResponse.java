package ar.edu.iessf.servife.gestion.dto;

import java.time.Instant;
import java.util.UUID;

/** Un aviso del usuario (E11). {@code uuidSolicitud} es null si no está asociado a una solicitud. */
public record AvisoResponse(UUID uuid, String tipo, String titulo, String cuerpo, UUID uuidSolicitud,
        boolean leida, Instant creadoEn) {
}
