package ar.edu.iessf.servife.identidad.service;

/** Regla única de contraseña (registro, recuperación): 8 a 72 caracteres (límite de BCrypt), con letra y número. */
public final class Contrasenias {

    public static final String REGLA = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String MENSAJE = "mínimo 8 caracteres, con al menos una letra y un número";

    private Contrasenias() {
    }
}
