package ar.edu.iessf.servife.identidad.dto;

import java.util.UUID;

/** Una dirección guardada del cliente. */
public record DireccionResponse(UUID uuid, String alias, String direccion, boolean principal) {
}
