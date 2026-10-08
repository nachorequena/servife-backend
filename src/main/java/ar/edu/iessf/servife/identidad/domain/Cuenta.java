package ar.edu.iessf.servife.identidad.domain;

import java.util.UUID;

import ar.edu.iessf.servife.common.seguridad.Rol;

/**
 * Lo que tienen en común las tres cuentas (Cliente, Prestador y Gestor): lo que necesitan el
 * login, el refresh y la recuperación de contraseña sin saber de qué tabla vienen.
 */
public interface Cuenta {

    /** PK interna: solo para los services del backend (p. ej. avisos); nunca en DTOs. */
    Long getId();

    UUID getUuid();

    Rol getRol();

    String getEmail();

    /** Hash BCrypt; nunca la contraseña en claro. */
    String getContrasenia();

    String getNombreApellido();

    EstadoCuenta getEstadoCuenta();

    boolean estaEliminado();

    /** Reemplaza el hash de la contraseña (el servicio ya lo calculó con el PasswordEncoder). */
    void cambiarContrasenia(String hash);
}
