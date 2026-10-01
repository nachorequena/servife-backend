package ar.edu.iessf.servife.identidad.dto;

import java.time.LocalDate;
import java.util.UUID;

import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.identidad.domain.EstadoValidacion;

/**
 * Datos públicos de una cuenta. Nunca lleva la contraseña ni el id BIGINT.
 * telefono, direccion y fecNacimiento son null para el gestor; estadoValidacion solo para el prestador.
 */
public record UsuarioResponse(UUID uuid, Rol rol, String nombreApellido, String email,
    String telefono, String direccion, LocalDate fecNacimiento, EstadoValidacion estadoValidacion) {
}
