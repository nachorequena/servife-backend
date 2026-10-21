package ar.edu.iessf.servife.gestion.dto;

import java.time.Instant;
import java.util.UUID;

import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.identidad.domain.EstadoCuenta;
import ar.edu.iessf.servife.identidad.domain.EstadoValidacion;

/**
 * Un usuario visto por el gestor (E3, E4, E13, E14). telefono es null en los gestores; estadoValidacion
 * y tipoServicio (el nombre del rubro) solo vienen en los prestadores. Nunca expone el id BIGINT.
 */
public record UsuarioAdminResponse(UUID uuid, Rol rol, String nombreApellido, String email, String telefono,
        EstadoCuenta estadoCuenta, EstadoValidacion estadoValidacion, String tipoServicio, Instant creadoEn) {
}
