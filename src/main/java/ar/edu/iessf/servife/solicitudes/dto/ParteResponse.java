package ar.edu.iessf.servife.solicitudes.dto;

import java.util.UUID;

/** Cliente o prestador de una solicitud. El teléfono solo viaja desde que la solicitud está ACEPTADA. */
public record ParteResponse(UUID uuid, String nombreApellido, String telefono) {
}
