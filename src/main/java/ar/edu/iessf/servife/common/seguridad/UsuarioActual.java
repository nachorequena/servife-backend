package ar.edu.iessf.servife.common.seguridad;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import ar.edu.iessf.servife.config.JwtConfig;

/**
 * Quién hace la request, leído del JWT: sub = uuid del usuario, rol = claim "rol".
 * Los services lo usan para filtrar por dueño (recurso ajeno = 404, .ai/07-security.md).
 */
@Component
public class UsuarioActual {

    public UUID uuid() {
        return UUID.fromString(jwt().getSubject());
    }

    public Rol rol() {
        return Rol.valueOf(jwt().getClaimAsString(JwtConfig.CLAIM_ROL));
    }

    private Jwt jwt() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || !(autenticacion.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("No hay un usuario autenticado con JWT en esta request");
        }
        return jwt;
    }
}
