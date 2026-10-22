package ar.edu.iessf.servife.config;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import ar.edu.iessf.servife.common.error.EscritorDeErrores;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.identidad.domain.Cuenta;
import ar.edu.iessf.servife.identidad.domain.EstadoCuenta;
import ar.edu.iessf.servife.identidad.service.Cuentas;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Corta en el acto la sesión de una cuenta suspendida o dada de baja (E4, E15), sin esperar a que venza
 * el access token: en cada pedido con JWT válido busca la cuenta por uuid y rol. Dada de baja o
 * inexistente: 401 NO_AUTENTICADO; suspendida: 403 CUENTA_SUSPENDIDA. Los pedidos públicos no traen
 * token y pasan de largo.
 *
 * Cuentas llega por ObjectProvider: en los @WebMvcTest no hay servicios y el filtro no actúa.
 */
public class FiltroDeCuentaActiva extends OncePerRequestFilter {

    private final ObjectProvider<Cuentas> cuentas;
    private final EscritorDeErrores escritorDeErrores;

    public FiltroDeCuentaActiva(ObjectProvider<Cuentas> cuentas, EscritorDeErrores escritorDeErrores) {
        this.cuentas = cuentas;
        this.escritorDeErrores = escritorDeErrores;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain cadena)
            throws ServletException, IOException {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        Cuentas buscador = cuentas.getIfAvailable();
        if (buscador == null || autenticacion == null || !(autenticacion.getPrincipal() instanceof Jwt jwt)) {
            cadena.doFilter(request, response);
            return;
        }
        Optional<Cuenta> cuenta = buscar(buscador, jwt);
        if (cuenta.isEmpty()) {
            SecurityContextHolder.clearContext();
            escritorDeErrores.noAutenticado(request, response, null);
            return;
        }
        if (cuenta.get().getEstadoCuenta() == EstadoCuenta.SUSPENDIDA) {
            SecurityContextHolder.clearContext();
            escritorDeErrores.cuentaSuspendida(request, response);
            return;
        }
        cadena.doFilter(request, response);
    }

    /** Un token con sub o rol que no se pueden leer se trata como el de una cuenta inexistente. */
    private static Optional<Cuenta> buscar(Cuentas buscador, Jwt jwt) {
        try {
            return buscador.buscarPorUuid(UUID.fromString(jwt.getSubject()),
                Rol.valueOf(jwt.getClaimAsString(JwtConfig.CLAIM_ROL)));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
