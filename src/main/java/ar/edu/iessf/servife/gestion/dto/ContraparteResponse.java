package ar.edu.iessf.servife.gestion.dto;

import java.util.UUID;

/** La otra parte de un chat, vista por el usuario actual (el cliente ve al prestador y viceversa). */
public record ContraparteResponse(UUID uuid, String nombreApellido) {
}
