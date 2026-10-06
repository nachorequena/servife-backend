package ar.edu.iessf.servife.identidad.dto;

import ar.edu.iessf.servife.common.seguridad.Rol;

/** A2 y A3 · respuesta con el access JWT (15 min), el refresh opaco (7 días) y el rol. */
public record TokensResponse(String accessToken, String refreshToken, Rol rol) {
}
