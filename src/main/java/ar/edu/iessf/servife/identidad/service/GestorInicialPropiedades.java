package ar.edu.iessf.servife.identidad.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Datos del primer gestor (variables GESTOR_INICIAL_EMAIL, GESTOR_INICIAL_CONTRASENIA y
 * GESTOR_INICIAL_NOMBRE). Vacíos por defecto: sin ellas no se crea nada.
 */
@ConfigurationProperties(prefix = "servife.gestor-inicial")
public record GestorInicialPropiedades(String email, String contrasenia, String nombre) {

    @Override
    public String toString() {
        return "GestorInicialPropiedades[email=" + email + ", nombre=" + nombre + "]";
    }
}
