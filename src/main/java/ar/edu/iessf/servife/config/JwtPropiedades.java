package ar.edu.iessf.servife.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del JWT (.ai/07-security.md): access de 15 minutos, refresh opaco de 7 días.
 * El secreto llega por la variable de entorno JWT_SECRETO.
 */
@ConfigurationProperties(prefix = "servife.jwt")
public record JwtPropiedades(String secreto, Duration duracionAccess, Duration duracionRefresh) {

    public JwtPropiedades {
        if (secreto == null || secreto.length() < 32) {
            throw new IllegalStateException("JWT_SECRETO tiene que tener al menos 32 caracteres");
        }
    }
}
