package ar.edu.iessf.servife.identidad.service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.iessf.servife.common.error.NegocioException;
import ar.edu.iessf.servife.identidad.domain.Cuenta;
import ar.edu.iessf.servife.identidad.domain.EstadoCuenta;
import ar.edu.iessf.servife.identidad.domain.RefreshToken;
import ar.edu.iessf.servife.identidad.dto.LoginRequest;
import ar.edu.iessf.servife.identidad.dto.RefreshRequest;
import ar.edu.iessf.servife.identidad.dto.TokensResponse;

/** A2 (login) y A3 (refresh con rotación) · CU02. No registra contraseñas ni tokens en el log. */
@Service
public class ServicioDeSesion {

    /** BCrypt (cost 10) de una clave que nadie usa: el email inexistente igual paga el costo de matches. */
    private static final String HASH_FICTICIO = "$2a$10$3cEZLd0PNfYRbjbZ8tcDde5gk0V3nl5eRsbT3RG8LKW3.Vi6z3v6W";
    private static final int MAX_BYTES_CONTRASENIA = 72;

    private final Cuentas cuentas;
    private final ServicioDeTokens tokens;
    private final PasswordEncoder passwordEncoder;

    public ServicioDeSesion(Cuentas cuentas, ServicioDeTokens tokens, PasswordEncoder passwordEncoder) {
        this.cuentas = cuentas;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Mismo 401 si el email no existe (o está dado de baja) o la contraseña no coincide. La suspensión
     * solo se revela con la contraseña correcta. El prestador PENDIENTE o RECHAZADO sí entra.
     */
    public TokensResponse iniciar(LoginRequest pedido) {
        // BCrypt solo mira 72 bytes y puede fallar con más: ninguna contraseña válida los pasa.
        if (pedido.contrasenia().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES_CONTRASENIA) {
            throw credencialesInvalidas();
        }
        Optional<Cuenta> cuenta = cuentas.buscarPorEmail(pedido.email());
        String hash = cuenta.map(Cuenta::getContrasenia).orElse(HASH_FICTICIO);
        boolean coincide = passwordEncoder.matches(pedido.contrasenia(), hash);
        if (cuenta.isEmpty() || !coincide) {
            throw credencialesInvalidas();
        }
        verificarActiva(cuenta.get());
        return tokens.emitir(cuenta.get().getUuid(), cuenta.get().getRol());
    }

    /** Rota el refresh: consume el viejo y emite un par nuevo. Cuenta inexistente o dada de baja: 401; suspendida: 403. */
    @Transactional
    public TokensResponse renovar(RefreshRequest pedido) {
        RefreshToken consumido = tokens.consumir(pedido.refreshToken());
        Cuenta cuenta = cuentas.buscarPorUuid(consumido.getUuidUsuario(), consumido.getRol())
            .orElseThrow(() -> new NegocioException(HttpStatus.UNAUTHORIZED, "REFRESH_INVALIDO",
                "Tu sesión venció. Ingresá de nuevo."));
        verificarActiva(cuenta);
        return tokens.emitir(cuenta.getUuid(), cuenta.getRol());
    }

    private static void verificarActiva(Cuenta cuenta) {
        if (cuenta.getEstadoCuenta() == EstadoCuenta.SUSPENDIDA) {
            throw new NegocioException(HttpStatus.FORBIDDEN, "CUENTA_SUSPENDIDA", "Tu cuenta está suspendida.");
        }
    }

    private static NegocioException credencialesInvalidas() {
        return new NegocioException(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS",
            "El correo o la contraseña no son correctos.");
    }
}
