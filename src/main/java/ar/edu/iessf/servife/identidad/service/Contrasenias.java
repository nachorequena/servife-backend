package ar.edu.iessf.servife.identidad.service;

import java.nio.charset.StandardCharsets;

import ar.edu.iessf.servife.common.error.ValidacionException;

/** Regla única de contraseña (registro, recuperación): 8 a 72 caracteres (límite de BCrypt), con letra y número. */
public final class Contrasenias {

    public static final String REGLA = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String MENSAJE = "mínimo 8 caracteres, con al menos una letra y un número";

    /** BCrypt trabaja con bytes: pasa de 72 y falla aunque la regex (que cuenta caracteres) la acepte. */
    private static final int MAX_BYTES = 72;

    private Contrasenias() {
    }

    /** Rechaza con ValidacionException las contraseñas de más de 72 bytes en UTF-8 (ej.: muchas "ñ"). */
    public static void validarLargo(String campo, String contrasenia) {
        if (contrasenia.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new ValidacionException(campo, "es demasiado larga");
        }
    }
}
