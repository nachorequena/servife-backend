package ar.edu.iessf.servife.gestion.service;

import ar.edu.iessf.servife.identidad.domain.EstadoCuenta;

/** E4 suspendió o reactivó una cuenta; el correo sale después del commit (EnvioDeAvisoDeEstadoDeCuenta). motivo puede ser null. */
public record EstadoDeCuentaCambiado(String email, String nombreApellido, EstadoCuenta estado, String motivo) {
}
